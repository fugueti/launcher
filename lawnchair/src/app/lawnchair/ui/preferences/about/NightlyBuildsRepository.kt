package app.lawnchair.ui.preferences.about

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import app.fugueti.update.UpdateChannel
import app.fugueti.update.UpdateCheckResult
import app.fugueti.update.UpdateChecker
import app.lawnchair.util.getApkVersionComparison
import com.android.launcher3.BuildConfig
import java.io.File
import java.io.IOException
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Update state for the About screen. Fugueti: despite the name it serves both channels, the
 * GitHub release for the release app and the nightly pre-release for the nightly app
 * ([UpdateChecker]); download and install are shared.
 */
class NightlyBuildsRepository(
    val applicationContext: Context,
    val api: GitHubService,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val updateState: StateFlow<UpdateState>
        field = MutableStateFlow<UpdateState>(UpdateState.UpToDate)

    fun checkForUpdate() {
        val channel = UpdateChannel.current ?: return
        coroutineScope.launch(Dispatchers.Default) {
            updateState.update { UpdateState.Checking }
            try {
                when (val result = UpdateChecker(api, channel).check(withChangelog = true)) {
                    is UpdateCheckResult.Available -> updateState.update {
                        val update = result.update
                        UpdateState.Available(
                            update.assetName,
                            update.url,
                            changelogState = update.changelog,
                            expectedSha256 = update.sha256,
                        )
                    }

                    UpdateCheckResult.UpToDate -> updateState.update { UpdateState.UpToDate }

                    UpdateCheckResult.OtherBranch -> updateState.update {
                        UpdateState.Disabled(UpdateDisabledReason.MAJOR_IS_NEWER)
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is IOException -> {
                        Log.e(TAG, "Network error during update check", e)
                    }

                    else -> {
                        Log.e(TAG, "Failed to check for update", e)
                    }
                }
                updateState.update { UpdateState.Failed }
            }
        }
    }

    fun downloadUpdate() {
        val currentState = updateState.value
        if (currentState !is UpdateState.Available) return

        coroutineScope.launch(Dispatchers.IO) {
            updateState.update { UpdateState.Downloading(0f) }
            try {
                val file = downloadApk(currentState.url, currentState.expectedSha256) { progress ->
                    updateState.update { UpdateState.Downloading(progress) }
                }
                if (file != null) {
                    updateState.update { UpdateState.Downloaded(file) }
                } else {
                    Log.e(TAG, "Downloaded file is null")
                    updateState.update { UpdateState.Failed }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                updateState.update { UpdateState.Failed }
            }
        }
    }

    fun installUpdate(file: File, forceInstall: Boolean = false) {
        if (!forceInstall && applicationContext.isApkMajorVersionNewer(file)) {
            updateState.update { UpdateState.MajorUpdate(file) }
            return
        }
        // Fugueti: always hand the APK to the system installer. It asks for the "install unknown
        // apps" permission itself, and Android 17's "Ask every time" mode reports no permission
        // here although the installer would just ask.
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
    }

    fun resetToDownloaded(file: File) {
        updateState.update { UpdateState.Downloaded(file) }
    }

    private suspend fun downloadApk(url: String, expectedSha256: String?, onProgress: (Float) -> Unit): File? {
        return try {
            val cacheDir = applicationContext.cacheDir
            val apkDirPath = cacheDir.toPath().resolve("updates").createDirectories()
            val apkFilePath = apkDirPath.resolve("Lawnchair-update.apk").apply { deleteIfExists() }

            val responseBody = api.downloadFile(url)
            val totalBytes = responseBody.contentLength().toFloat()
            if (totalBytes <= 0) {
                Log.w(TAG, "Content length is invalid: $totalBytes")
                return null
            }

            val messageDigest = java.security.MessageDigest.getInstance("SHA-256")

            responseBody.byteStream().use { input ->
                apkFilePath.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        messageDigest.update(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        onProgress(bytesDownloaded / totalBytes)
                    }
                }
            }
            if (expectedSha256 != null) {
                val computedHash = messageDigest.digest().joinToString("") { "%02x".format(it) }
                if (!computedHash.equals(expectedSha256, ignoreCase = true)) {
                    Log.e(TAG, "SHA256 verification failed. Expected: $expectedSha256, Got: $computedHash")
                    apkFilePath.deleteIfExists()
                    return null
                }
                Log.d(TAG, "SHA256 verification passed: $computedHash")
            }

            apkFilePath.toFile()
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "NightlyBuildsRepository"
    }
}

/**
 * Checks if the downloaded APK file has a higher Major (AA) version than the currently
 * installed build.
 */
private fun Context.isApkMajorVersionNewer(apkFile: File): Boolean {
    val (currentParsed, apkParsed) = getApkVersionComparison(apkFile) ?: return false

    val apkMajor = apkParsed[0]
    val currentMajor = currentParsed[0]

    Log.d("UpdateCheck", "Current Major: $currentMajor, APK Major: $apkMajor")

    return apkMajor > currentMajor
}
