/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.LauncherPrefs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupStoreTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun sharedPrefsXmlReplacesLiveValues() {
        val prefs = LauncherPrefs.getPrefs(context)
        // Values the app holds in memory before the restore; "stale" is not in the backup.
        prefs.edit().putString("pref_iconPackPackage", "old.pack").putBoolean("stale", true).commit()

        val xml = File.createTempFile("prefs", ".xml").apply {
            writeText(
                """
                <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
                <map>
                    <string name="pref_iconPackPackage">com.example.icons</string>
                    <string name="pref_font">Sans &amp; "Serif"</string>
                    <int name="pref_workspaceColumns" value="5" />
                    <long name="pref_lastUpdate" value="1732147200000" />
                    <float name="pref_iconSizeFactor" value="1.15" />
                    <boolean name="pref_showStatusBar" value="false" />
                    <set name="pref_hiddenApps">
                        <string>com.example.a/.Main#0</string>
                        <string>com.example.b/.Main#0</string>
                    </set>
                </map>
                """.trimIndent(),
            )
        }
        BackupStore.applySharedPrefs(context, xml)

        assertEquals("com.example.icons", prefs.getString("pref_iconPackPackage", null))
        assertEquals("Sans & \"Serif\"", prefs.getString("pref_font", null))
        assertEquals(5, prefs.getInt("pref_workspaceColumns", 0))
        assertEquals(1732147200000L, prefs.getLong("pref_lastUpdate", 0))
        assertEquals(1.15f, prefs.getFloat("pref_iconSizeFactor", 0f), 0.0001f)
        assertFalse(prefs.getBoolean("pref_showStatusBar", true))
        assertEquals(setOf("com.example.a/.Main#0", "com.example.b/.Main#0"), prefs.getStringSet("pref_hiddenApps", null))
        // Restoring replaces the whole set of preferences.
        assertFalse(prefs.contains("stale"))
    }

    @Test
    fun extraFilesRoundTripAndRejectUnsafeNames() {
        val fonts = File(context.filesDir, "customFonts").apply { mkdirs() }
        File(fonts, "My Font.ttf").writeText("font")
        val entries = BackupStore.extraFiles(context)
        assertTrue("files/customFonts/My Font.ttf" in entries)

        File(fonts, "My Font.ttf").delete()
        BackupStore.restoreExtraFile(context, "files/customFonts/My Font.ttf", "font".byteInputStream())
        assertEquals("font", File(fonts, "My Font.ttf").readText())

        // Unknown folders and path tricks are ignored.
        BackupStore.restoreExtraFile(context, "files/../evil.txt", "x".byteInputStream())
        BackupStore.restoreExtraFile(context, "files/databases/evil.db", "x".byteInputStream())
        BackupStore.restoreExtraFile(context, "files/customFonts/a/b.ttf", "x".byteInputStream())
        assertFalse(File(context.filesDir.parentFile, "evil.txt").exists())
        assertFalse(File(context.filesDir, "databases/evil.db").exists())
        assertFalse(File(fonts, "a/b.ttf").exists())
    }
}
