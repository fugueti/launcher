/*
 * Copyright (C) 2026 Fugueti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Turns the dex2jar output of an Android {@code framework.jar} into a compile-only stub JAR.
 *
 * <ul>
 *   <li>Every method body becomes {@code throw new RuntimeException("Stub!")} (like the SDK's
 *       {@code android.jar}); private methods and static initializers are dropped. Only
 *       signatures and constants remain, so no implementation code is redistributed.</li>
 *   <li>Class files are raised to version 52 (Java 8): dex2jar writes version 50 class files
 *       that may contain default methods, which javac rejects.</li>
 *   <li>InnerClasses entries are added for every referenced nested class. dex2jar only records
 *       a class's own nested classes, and Kotlin/KSP need the others to resolve names such as
 *       {@code Drawable.Callback} from {@code View}.</li>
 *   <li>Entries are written in sorted order with a fixed timestamp, so the output is
 *       reproducible.</li>
 * </ul>
 *
 * usage: java -cp asm.jar StubJar.java in.jar out.jar
 */
public class StubJar {

    private record Nested(String outer, String simpleName, int access) {}

    private static final long FIXED_TIME = 315532800000L; // 1980-01-01, the ZIP epoch

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: StubJar in.jar out.jar");
            System.exit(2);
        }
        Map<String, byte[]> classes = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(args[0]))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().endsWith(".class")) {
                    classes.put(e.getName(), readAll(in));
                }
            }
        }

        // Pass 1: nested class declarations, from each class's own InnerClasses entries.
        Map<String, Nested> nested = new HashMap<>();
        for (byte[] data : classes.values()) {
            new ClassReader(data).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitInnerClass(String name, String outer, String inner, int access) {
                    if (outer != null && inner != null) {
                        nested.putIfAbsent(name, new Nested(outer, inner, access));
                    }
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        int innerAdded = 0;
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            for (Map.Entry<String, byte[]> en : classes.entrySet()) {
                byte[] stub = stub(en.getValue(), nested);
                innerAdded += lastInnerAdded;
                ZipEntry entry = new ZipEntry(en.getKey());
                entry.setTime(FIXED_TIME);
                out.putNextEntry(entry);
                out.write(stub);
                out.closeEntry();
            }
        }
        System.out.println("classes: " + classes.size() + ", nested classes: " + nested.size()
                + ", InnerClasses entries added: " + innerAdded);
    }

    private static int lastInnerAdded;

    private static byte[] stub(byte[] data, Map<String, Nested> nested) {
        ClassReader reader = new ClassReader(data);
        Set<String> referenced = referencedClasses(reader, data);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Set<String> existingInner = new HashSet<>();
        List<String> missingInner = new ArrayList<>();

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                int major = version & 0xFFFF;
                super.visit(Math.max(major, Opcodes.V1_8), access, name, signature, superName,
                        interfaces);
            }

            @Override
            public void visitInnerClass(String name, String outer, String inner, int access) {
                existingInner.add(name);
                super.visitInnerClass(name, outer, inner, access);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_PRIVATE) != 0 || "<clinit>".equals(name)) {
                    return null;
                }
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature,
                        exceptions);
                boolean hasBody = (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitEnd() {
                        if (hasBody) {
                            super.visitCode();
                            super.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
                            super.visitInsn(Opcodes.DUP);
                            super.visitLdcInsn("Stub!");
                            super.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                    "java/lang/RuntimeException", "<init>",
                                    "(Ljava/lang/String;)V", false);
                            super.visitInsn(Opcodes.ATHROW);
                            super.visitMaxs(0, 0);
                        }
                        super.visitEnd();
                    }
                };
            }

            @Override
            public void visitEnd() {
                for (String r : referenced) {
                    // Add the class and all its enclosing nested classes.
                    String cur = r;
                    while (nested.containsKey(cur)) {
                        if (!existingInner.contains(cur) && !missingInner.contains(cur)) {
                            missingInner.add(cur);
                        }
                        cur = nested.get(cur).outer();
                    }
                }
                for (String m : missingInner) {
                    Nested n = nested.get(m);
                    super.visitInnerClass(m, n.outer(), n.simpleName(), n.access());
                }
                super.visitEnd();
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        lastInnerAdded = missingInner.size();
        return writer.toByteArray();
    }

    private static final java.util.regex.Pattern TYPE_IN_SIGNATURE =
            java.util.regex.Pattern.compile("L([A-Za-z0-9_/$]+)[;<]");

    /**
     * Class names referenced by the class file: CONSTANT_Class entries plus the types named in
     * descriptors and generic signatures (UTF8 entries). A superset is fine, extra InnerClasses
     * entries are harmless.
     */
    private static Set<String> referencedClasses(ClassReader reader, byte[] data) {
        Set<String> refs = new java.util.TreeSet<>();
        char[] buf = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int off = reader.getItem(i);
            if (off <= 0) continue;
            int tag = data[off - 1];
            if (tag == 7 /* CONSTANT_Class */) {
                String n = reader.readUTF8(off, buf);
                if (n != null && !n.startsWith("[")) refs.add(n);
            } else if (tag == 1 /* CONSTANT_Utf8 */) {
                // Entry layout: u2 length, then modified UTF-8 bytes (ASCII for type names)
                int len = ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
                String u = new String(data, off + 2, len,
                        java.nio.charset.StandardCharsets.ISO_8859_1);
                if (u != null && u.indexOf('L') >= 0) {
                    java.util.regex.Matcher m = TYPE_IN_SIGNATURE.matcher(u);
                    while (m.find()) refs.add(m.group(1));
                }
            }
        }
        return refs;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        in.transferTo(bos);
        return bos.toByteArray();
    }
}
