/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.backup

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import app.lawnchair.data.AppDatabase
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.LauncherPrefs
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.w3c.dom.Element

/**
 * Backup and restore helpers that work with the running app instead of overwriting its files.
 *
 * Copying a backed up file over a SharedPreferences file, a DataStore or a Room database while the
 * app holds them in memory does not stick: the next write from the app saves its in-memory copy
 * back (upstream's grid state write right after the copy did exactly that), and replacing an open
 * SQLite file loses or mixes data. So backups are written from consolidated files, and restores
 * apply each part through the live API.
 */
object BackupStore {
    private const val TAG = "FuguetiBackup"

    /** Folder of extra files inside the backup zip (fonts, saved wallpapers). */
    const val FILES_PREFIX = "files/"

    /** Folders of [Context.getFilesDir] that go into the backup. */
    private val EXTRA_DIRS = listOf("customFonts", "wallpapers")

    /** Room tables of [AppDatabase] (icon overrides, saved wallpapers, drawer folders). */
    private val APP_DB_TABLES = listOf("IconOverride", "Wallpapers", "Folders", "FolderItems")

    // region backup

    /** Makes sure everything is on disk before the files are copied into a backup. */
    suspend fun prepareForBackup(context: Context, launcherDb: File) {
        // Pending apply() calls are written by an empty commit().
        LauncherPrefs.getPrefs(context).edit().commit()
        // Room and the launcher database keep recent changes in their -wal file.
        runCatching { AppDatabase.INSTANCE.get(context).checkpoint() }
            .onFailure { Log.w(TAG, "Room checkpoint failed", it) }
        checkpoint(launcherDb)
    }

    private fun checkpoint(dbFile: File) {
        if (!dbFile.exists()) return
        runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            }
        }.onFailure { Log.w(TAG, "Checkpoint of ${dbFile.name} failed", it) }
    }

    /** Extra files to put in the backup, by zip entry name. */
    fun extraFiles(context: Context): Map<String, File> = EXTRA_DIRS.flatMap { dir ->
        File(context.filesDir, dir).listFiles().orEmpty()
            .filter { it.isFile }
            .map { "$FILES_PREFIX$dir/${it.name}" to it }
    }.toMap()

    // endregion

    // region restore

    /** Writes an extra file entry (`files/<dir>/<name>`) back to the files dir. */
    fun restoreExtraFile(context: Context, entryName: String, input: InputStream) {
        val parts = entryName.removePrefix(FILES_PREFIX).split('/')
        if (parts.size != 2 || parts[0] !in EXTRA_DIRS) return
        val name = parts[1]
        if (name.isEmpty() || name == "." || name == "..") return
        val dir = File(context.filesDir, parts[0]).apply { mkdirs() }
        File(dir, name).outputStream().use { input.copyTo(it) }
    }

    /** Replaces the launcher's SharedPreferences with the values of a backed up prefs XML. */
    fun applySharedPrefs(context: Context, xmlFile: File) {
        if (!xmlFile.exists()) return
        val root = runCatching {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile).documentElement
        }.getOrElse {
            Log.w(TAG, "Unreadable prefs backup", it)
            return
        }
        val editor = LauncherPrefs.getPrefs(context).edit().clear()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            putPref(editor, node)
        }
        editor.commit()
    }

    private fun putPref(editor: SharedPreferences.Editor, node: Element) {
        val name = node.getAttribute("name").takeIf { it.isNotEmpty() } ?: return
        val value = node.getAttribute("value")
        runCatching {
            when (node.tagName) {
                "string" -> editor.putString(name, node.textContent ?: "")
                "int" -> editor.putInt(name, value.toInt())
                "long" -> editor.putLong(name, value.toLong())
                "float" -> editor.putFloat(name, value.toFloat())
                "boolean" -> editor.putBoolean(name, value.toBoolean())
                "set" -> {
                    val items = node.getElementsByTagName("string")
                    editor.putStringSet(name, (0 until items.length).map { items.item(it).textContent }.toSet())
                }
                else -> Unit
            }
        }.onFailure { Log.w(TAG, "Skipping pref $name", it) }
    }

    /** Replaces Fugueti's DataStore settings with the ones of a backed up `.preferences_pb`. */
    suspend fun applyDataStore(context: Context, pbFile: File) {
        if (!pbFile.exists()) return
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val restored = PreferenceDataStoreFactory.create(scope = scope, produceFile = { pbFile }).data.first()
            PreferenceManager2.getInstance(context).preferencesDataStore.edit { prefs ->
                prefs.clear()
                prefs += restored
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unreadable settings backup", t)
        } finally {
            scope.cancel()
        }
    }

    /**
     * Imports the rows of a backed up [AppDatabase] file into the live one, table by table and
     * only for the columns both have. Saved wallpaper paths are moved into this app's files dir.
     */
    fun importAppDatabase(context: Context, dbFile: File) {
        if (!dbFile.exists()) return
        val db = AppDatabase.INSTANCE.get(context).openHelper.writableDatabase
        runCatching {
            db.execSQL("ATTACH DATABASE ? AS backup", arrayOf(dbFile.path))
        }.onFailure {
            Log.w(TAG, "Unreadable database backup", it)
            return
        }
        try {
            db.beginTransaction()
            try {
                for (table in APP_DB_TABLES) {
                    val columns = columns(db, "main", table).intersect(columns(db, "backup", table).toSet())
                    if (columns.isEmpty()) continue
                    val list = columns.joinToString { "`$it`" }
                    db.execSQL("DELETE FROM main.`$table`")
                    db.execSQL("INSERT INTO main.`$table` ($list) SELECT $list FROM backup.`$table`")
                }
                val marker = "/files/wallpapers/"
                db.execSQL(
                    "UPDATE main.Wallpapers SET imagePath = ? || substr(imagePath, instr(imagePath, ?) + ?) " +
                        "WHERE instr(imagePath, ?) > 0",
                    arrayOf<Any>(File(context.filesDir, "wallpapers").path + "/", marker, marker.length, marker),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Database import failed", t)
        } finally {
            runCatching { db.execSQL("DETACH DATABASE backup") }
        }
    }

    private fun columns(db: androidx.sqlite.db.SupportSQLiteDatabase, schema: String, table: String): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            db.query("PRAGMA $schema.table_info(`$table`)").use { c ->
                val nameIndex = c.getColumnIndex("name")
                while (c.moveToNext()) result += c.getString(nameIndex)
            }
        }
        return result
    }

    // endregion
}
