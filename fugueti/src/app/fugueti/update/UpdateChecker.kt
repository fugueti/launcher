/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.update

import android.util.Log
import app.lawnchair.ui.preferences.about.ChangelogState
import app.lawnchair.ui.preferences.about.GitHubCommit
import app.lawnchair.ui.preferences.about.GitHubService
import app.lawnchair.ui.util.isPlayStoreFlavor
import com.android.launcher3.BuildConfig

/** Where this build gets its updates from. */
enum class UpdateChannel {
    /** `app.fugueti.launcher`: the GitHub release marked "Latest". */
    Release,

    /** `app.fugueti.launcher.nightly`: the `nightly` pre-release. */
    Nightly,
    ;

    companion object {
        /** Null when this build does not update itself (Play Store and debug builds). */
        val current: UpdateChannel?
            get() = when {
                isPlayStoreFlavor() || BuildConfig.DEBUG -> null
                BuildConfig.APPLICATION_ID.contains("nightly") -> Nightly
                else -> Release
            }
    }
}

/** An update found on GitHub. [label] is what the user sees: `16.0.3` or `nightly #12`. */
data class FoundUpdate(
    val label: String,
    val assetName: String,
    val url: String,
    val sha256: String?,
    val changelog: ChangelogState?,
)

sealed interface UpdateCheckResult {
    data class Available(val update: FoundUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult

    /** The nightly release is built from another branch (another major version). */
    data object OtherBranch : UpdateCheckResult
}

/** Looks for a newer build of [channel] on GitHub. Network errors are thrown to the caller. */
class UpdateChecker(
    private val api: GitHubService,
    private val channel: UpdateChannel,
) {
    private val owner = BuildConfig.GITHUB_REPO.substringBefore("/")
    private val repo = BuildConfig.GITHUB_REPO.substringAfter("/")

    suspend fun check(withChangelog: Boolean): UpdateCheckResult = when (channel) {
        UpdateChannel.Release -> checkRelease(withChangelog)
        UpdateChannel.Nightly -> checkNightly(withChangelog)
    }

    private suspend fun checkRelease(withChangelog: Boolean): UpdateCheckResult {
        val release = api.getLatestRelease()
        val current = BuildConfig.FUGUETI_VERSION
        if (!AppVersion.isNewer(release.tagName, current)) return UpdateCheckResult.UpToDate
        val asset = release.assets.firstOrNull { it.name.endsWith(RELEASE_APK_SUFFIX) }
            ?: return UpdateCheckResult.UpToDate
        val latest = release.tagName.removePrefix("v")
        val changelog = if (withChangelog) {
            val commits = runCatching {
                // Oldest first from GitHub; the dialog lists the newest first.
                api.compareCommits(owner, repo, "v$current", release.tagName).commits.reversed()
            }.onFailure { Log.e(TAG, "Failed to get commits", it) }.getOrNull()
            commits?.let { ChangelogState(commits = it, currentVersion = current, latestVersion = latest) }
        } else {
            null
        }
        return UpdateCheckResult.Available(
            FoundUpdate(latest, asset.name, asset.browserDownloadUrl, asset.sha256Hash, changelog),
        )
    }

    private suspend fun checkNightly(withChangelog: Boolean): UpdateCheckResult {
        val nightly = api.getReleases().firstOrNull { it.tagName == "nightly" }
        if (nightly != null && nightly.targetCommitish != NIGHTLY_BRANCH) {
            Log.d(TAG, "Skipping update from branch ${nightly.targetCommitish}, expected $NIGHTLY_BRANCH")
            return UpdateCheckResult.OtherBranch
        }
        val asset = nightly?.assets?.firstOrNull() ?: return UpdateCheckResult.UpToDate

        // CI builds show "<major>.Dev.(#<CI run>)", see build.gradle.
        val currentBuild = BuildConfig.VERSION_DISPLAY_NAME
            .substringAfterLast("#")
            .removeSuffix(")")
            .toIntOrNull() ?: 0
        // Asset name: Fugueti.Nightly-CI_<run>-<sha>.apk
        val latestBuild = asset.name.substringAfter("_").substringBefore("-").toIntOrNull() ?: 0
        if (latestBuild <= currentBuild) return UpdateCheckResult.UpToDate

        val changelog = if (withChangelog) {
            nightlyCommits()?.let {
                ChangelogState(commits = it, currentBuildNumber = currentBuild, latestBuildNumber = latestBuild)
            }
        } else {
            null
        }
        return UpdateCheckResult.Available(
            FoundUpdate("nightly #$latestBuild", asset.name, asset.browserDownloadUrl, asset.sha256Hash, changelog),
        )
    }

    /** Commits on the nightly branch since this build, newest first. */
    private suspend fun nightlyCommits(): List<GitHubCommit>? = try {
        val commits = api.getRepositoryCommits(owner, repo, NIGHTLY_BRANCH)
        val currentIndex = commits.indexOfFirst { it.sha.startsWith(BuildConfig.COMMIT_HASH) }
        if (currentIndex > 0) commits.take(currentIndex) else commits.take(MAX_FALLBACK_COMMITS)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get commits", e)
        null
    }

    companion object {
        private const val TAG = "FuguetiUpdateChecker"
        private const val RELEASE_APK_SUFFIX = ".github.release.apk"
        private const val MAX_FALLBACK_COMMITS = 30

        /** Branch the nightly builds are made from. */
        const val NIGHTLY_BRANCH = "17-dev"
    }
}
