// Modified by Fugueti Launcher contributors (2026): see the "Fugueti" comments below.
package app.lawnchair.backup

import app.fugueti.backup.BackupStore

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import app.lawnchair.DeviceProfileOverrides
import app.lawnchair.LawnchairProto
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.hasFlag
import app.lawnchair.util.scaleDownTo
import app.lawnchair.util.scaleDownToDisplaySize
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.google.protobuf.Timestamp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LawnchairBackup(
    private val context: Context,
    private val uri: Uri,
) {
    lateinit var info: BackupInfo
    var screenshot: Bitmap? = null
    var wallpaper: Bitmap? = null

    suspend fun readInfoAndPreview() {
        var tmpScreenshot: Bitmap? = null
        var tmpWallpaper: Bitmap? = null
        readZip(
            mapOf(
                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },
                SCREENSHOT_FILE_NAME to { tmpScreenshot = BitmapFactory.decodeStream(it) },
                WALLPAPER_FILE_NAME to { tmpWallpaper = BitmapFactory.decodeStream(it) },
            ),
        )
        val size = max(info.previewWidth, info.previewHeight).coerceAtMost(4000)
        screenshot = tmpScreenshot?.scaleDownTo(size)
        wallpaper = tmpWallpaper?.scaleDownToDisplaySize(context)
    }

    suspend fun restore(selectedContents: Int) {
        val contents = selectedContents and info.contents
        // Fugueti: extract first, then apply each part through the running app (see BackupStore).
        val staging = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            readZipEntries { name, input ->
                when {
                    name.startsWith(BackupStore.FILES_PREFIX) -> {
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            BackupStore.restoreExtraFile(context, name, input)
                        }
                    }
                    name in STAGED_ENTRIES -> File(staging, name).outputStream().use { input.copyTo(it) }
                }
            }

            if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                // Home screen: staged as restored.db. The databases the running launcher has open
                // are left alone (deleting them crashed it with SQLITE_READONLY_DBMOVED); after the
                // restart ModelDbController moves restored.db in place of the grid's database and
                // runs RestoreDbTask on it, before anything else reads it.
                context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile?.listFiles()
                    ?.filter { it.name.startsWith(RESTORED_DB_FILE_NAME) }
                    ?.forEach { it.delete() }
                val stagedDb = File(staging, LAUNCHER_DB_FILE_NAME)
                if (stagedDb.exists()) {
                    val restoredDb = launcherDbFile(context, forRestore = true)
                    restoredDb.parentFile?.mkdirs()
                    // Owner first: this process restarts right after and must not take it.
                    context.getDatabasePath(RESTORED_DB_OWNER_FILE_NAME)
                        .writeText(android.os.Process.myPid().toString())
                    stagedDb.copyTo(restoredDb, overwrite = true)
                }
                BackupStore.applySharedPrefs(context, File(staging, PREFS_FILE_NAME))
                BackupStore.applyDataStore(context, File(staging, PREFS_DATASTORE_FILE_NAME))
                BackupStore.importAppDatabase(context, File(staging, PREFS_DB_FILE_NAME))
            }
            if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                val wallpaperFile = File(staging, WALLPAPER_FILE_NAME)
                if (wallpaperFile.exists()) {
                    WallpaperManager.getInstance(context).setBitmap(BitmapFactory.decodeFile(wallpaperFile.path))
                }
            }
            if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                // After the preferences: they are now the live values, so this no longer undoes them.
                DeviceGridState(info.gridState).writeToPrefs(context, true)
                // Fugueti: the restored home screen belongs to the backup's grid. A backup of a
                // default setup has no grid preferences, so Fugueti's own default grid stayed and
                // the layout was dropped when it did not fit.
                val backupGrid = applyBackupGrid(context, info.gridState)
                // The database file of the restored grid, which restored.db becomes on the next
                // start. A DB_FILE left from the old grid made the launcher open (and then delete)
                // the wrong one; an empty one made it "migrate" to the restored grid and reset
                // the restored layout to the default one.
                val dbFile = (backupGrid ?: DeviceProfileOverrides.DBGridInfo(PreferenceManager.getInstance(context))).dbFile
                LauncherPrefs.get(context).putSync(LauncherPrefs.DB_FILE.to(dbFile))
            }
        } catch (t: Throwable) {
            // Fugueti: a failed restore must not leave restored.db to be applied at a later start.
            context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile?.listFiles()
                ?.filter { it.name.startsWith(RESTORED_DB_FILE_NAME) }
                ?.forEach { it.delete() }
            throw t
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Fugueti: sets the home screen columns, rows and dock icons the backup was made with, and
     * returns that grid (null when the backup does not say).
     */
    private fun applyBackupGrid(context: Context, gridState: LawnchairProto.GridState): DeviceProfileOverrides.DBGridInfo? {
        val size = gridState.gridSize.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (size.size != 2 || gridState.hotseatCount <= 0) return null
        val prefs = PreferenceManager.getInstance(context)
        prefs.sp.edit()
            .putInt(prefs.workspaceColumns.key, size[0])
            .putInt(prefs.workspaceRows.key, size[1])
            .putInt(prefs.hotseatColumns.key, gridState.hotseatCount)
            .commit()
        return DeviceProfileOverrides.DBGridInfo(
            numHotseatColumns = gridState.hotseatCount,
            numRows = size[1],
            numColumns = size[0],
        )
    }

    /** Fugueti: calls [handler] for every entry of the backup zip. */
    private suspend fun readZipEntries(handler: suspend (String, InputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
            pfd.use {
                FileInputStream(it.fileDescriptor).use { inStream ->
                    ZipInputStream(inStream).use { zipIs ->
                        while (true) {
                            val entry = zipIs.nextEntry ?: break
                            if (entry.isDirectory || entry.name.contains("..")) continue
                            handler(entry.name, zipIs)
                        }
                    }
                }
            }
        }
    }

    private suspend fun readZip(handlers: Map<String, suspend (InputStream) -> Unit>) {
        withContext(Dispatchers.IO) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
            pfd.use {
                FileInputStream(it.fileDescriptor).use { inStream ->
                    ZipInputStream(inStream).use { zipIs ->
                        var entry: ZipEntry?
                        while (true) {
                            entry = zipIs.nextEntry
                            if (entry == null) break
                            handlers[entry.name]?.invoke(zipIs)
                        }
                    }
                }
            }
        }
    }

    companion object {
        // Fugueti: 2 adds custom fonts and saved wallpapers (files/...); 1 is still restorable
        private const val BACKUP_VERSION = 2
        private const val PREFS_FILE_NAME = "${LauncherFiles.SHARED_PREFERENCES_KEY}.xml"
        private const val PREFS_DB_FILE_NAME = "preferences"
        private const val PREFS_DATASTORE_FILE_NAME = "preferences.preferences_pb"

        const val INFO_FILE_NAME = "info.pb"
        const val WALLPAPER_FILE_NAME = "wallpaper.png"
        const val SCREENSHOT_FILE_NAME = "screenshot.png"
        const val LAUNCHER_DB_FILE_NAME = "launcher.db"
        const val RESTORED_DB_FILE_NAME = "restored.db"

        /** Fugueti: process id of the restore that staged restored.db (see LawnchairApp). */
        const val RESTORED_DB_OWNER_FILE_NAME = "restored.db.owner"

        /** Entries extracted to the staging folder on restore. */
        private val STAGED_ENTRIES = setOf(
            LAUNCHER_DB_FILE_NAME,
            PREFS_FILE_NAME,
            PREFS_DB_FILE_NAME,
            PREFS_DATASTORE_FILE_NAME,
            WALLPAPER_FILE_NAME,
        )

        const val INCLUDE_LAYOUT_AND_SETTINGS = 1 shl 0
        const val INCLUDE_WALLPAPER = 1 shl 1

        const val MIME_TYPE = "application/zip"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        val contentOptions = listOf(
            INCLUDE_LAYOUT_AND_SETTINGS to R.string.backup_content_layout_and_settings,
            INCLUDE_WALLPAPER to R.string.backup_content_wallpaper,
        )

        fun generateBackupFileName(): String {
            // Fugueti: YYYY-MM-DD_hh-mm.lawnchairbackup (24 h clock; no ":" so it works on any storage)
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
            return "$timestamp.lawnchairbackup"
        }

        fun getFiles(context: Context, forRestore: Boolean): Map<String, File> {
            return mapOf(
                LAUNCHER_DB_FILE_NAME to launcherDbFile(context, forRestore),
                PREFS_FILE_NAME to prefsFile(context),
                PREFS_DB_FILE_NAME to prefsDbFile(context),
                PREFS_DATASTORE_FILE_NAME to prefsDataStoreFile(context),
            )
        }

        @SuppressLint("MissingPermission")
        suspend fun create(context: Context, contents: Int, screenshotBitmap: Bitmap, fileUri: Uri) {
            val idp = LauncherAppState.getIDP(context)
            val createdAt = Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
            val colorHints = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors?.colorHints ?: 0
            val wallpaperSupportsDarkText = (colorHints and WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0
            val info = BackupInfo.newBuilder()
                .setLawnchairVersion(BuildConfig.VERSION_CODE)
                .setBackupVersion(BACKUP_VERSION)
                .setCreatedAt(createdAt)
                .setContents(contents)
                .setGridState(DeviceGridState(idp).toProtoMessage())
                .setPreviewWidth(screenshotBitmap.width)
                .setPreviewHeight(screenshotBitmap.height)
                .setPreviewDarkText(wallpaperSupportsDarkText)
                .build()

            // Fugueti: flush preferences and fold database -wal files into the copied files
            BackupStore.prepareForBackup(context, launcherDbFile(context, forRestore = false))

            val pfd = context.contentResolver.openFileDescriptor(fileUri, "w")!!
            withContext(Dispatchers.IO) {
                pfd.use {
                    ZipOutputStream(FileOutputStream(pfd.fileDescriptor).buffered()).use { out ->
                        out.putNextEntry(ZipEntry(INFO_FILE_NAME))
                        info.writeTo(out)

                        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                            val wallpaperManager = WallpaperManager.getInstance(context)
                            val wallpaperBitmap = wallpaperManager.drawable?.toBitmap()
                            if (wallpaperBitmap != null) {
                                out.putNextEntry(ZipEntry(WALLPAPER_FILE_NAME))
                                wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            out.putNextEntry(ZipEntry(SCREENSHOT_FILE_NAME))
                            screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                        }

                        getFiles(context, forRestore = false).entries.forEach {
                            if (!it.value.exists()) return@forEach
                            out.putNextEntry(ZipEntry(it.key))
                            it.value.inputStream().use { input -> input.copyTo(out) }
                        }
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            // Fugueti: custom fonts and saved wallpapers
                            BackupStore.extraFiles(context).forEach { (name, file) ->
                                out.putNextEntry(ZipEntry(name))
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                        }
                    }
                }
            }
        }

        private fun launcherDbFile(context: Context, forRestore: Boolean): File {
            val dbName = if (forRestore) RESTORED_DB_FILE_NAME else LauncherAppState.getIDP(context).dbFile
            return context.getDatabasePath(dbName)
        }

        private fun prefsFile(context: Context): File {
            val dir = context.cacheDir.parent
            return File(dir, "shared_prefs/$PREFS_FILE_NAME")
        }

        private fun prefsDbFile(context: Context): File {
            return context.getDatabasePath(PREFS_DB_FILE_NAME)
        }

        private fun prefsDataStoreFile(context: Context): File {
            return File(context.filesDir, "datastore/${PREFS_DATASTORE_FILE_NAME}")
        }
    }
}
