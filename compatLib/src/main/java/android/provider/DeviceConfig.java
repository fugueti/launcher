/*
 * Copyright (C) 2026 Fugueti Launcher contributors
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

package android.provider;

import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Compile-only stub. Since Android 17 DeviceConfig lives in the configinfrastructure mainline
 * module instead of framework.jar; at runtime the platform class from the boot classpath is used.
 * compatLib is compileOnly, so this never ends up in the APK.
 */
public final class DeviceConfig {
    public static final String NAMESPACE_APP_COMPAT = "app_compat";
    public static final String NAMESPACE_APP_COMPAT_OVERRIDES = "app_compat_overrides";
    public static final String NAMESPACE_LAUNCHER = "launcher";
    public static final String NAMESPACE_SYSTEMUI = "systemui";
    public static final String NAMESPACE_WINDOW_MANAGER = "window_manager";

    private DeviceConfig() { }

    public static boolean getBoolean(String namespace, String name, boolean defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public static int getInt(String namespace, String name, int defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public static long getLong(String namespace, String name, long defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public static float getFloat(String namespace, String name, float defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public static String getString(String namespace, String name, String defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public static String getProperty(String namespace, String name) {
        throw new RuntimeException("Stub!");
    }

    public static Properties getProperties(String namespace, String... names) {
        throw new RuntimeException("Stub!");
    }

    public static void addOnPropertiesChangedListener(String namespace, Executor executor,
            OnPropertiesChangedListener onPropertiesChangedListener) {
        throw new RuntimeException("Stub!");
    }

    public static void removeOnPropertiesChangedListener(
            OnPropertiesChangedListener onPropertiesChangedListener) {
        throw new RuntimeException("Stub!");
    }

    public interface OnPropertiesChangedListener {
        void onPropertiesChanged(Properties properties);
    }

    public static class Properties {
        public String getNamespace() {
            throw new RuntimeException("Stub!");
        }

        public Set<String> getKeyset() {
            throw new RuntimeException("Stub!");
        }

        public boolean getBoolean(String name, boolean defaultValue) {
            throw new RuntimeException("Stub!");
        }

        public int getInt(String name, int defaultValue) {
            throw new RuntimeException("Stub!");
        }

        public long getLong(String name, long defaultValue) {
            throw new RuntimeException("Stub!");
        }

        public float getFloat(String name, float defaultValue) {
            throw new RuntimeException("Stub!");
        }

        public String getString(String name, String defaultValue) {
            throw new RuntimeException("Stub!");
        }
    }
}
