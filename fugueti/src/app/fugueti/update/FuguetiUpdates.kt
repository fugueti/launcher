/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.update

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.about.GitHubService
import app.lawnchair.ui.preferences.about.gitHubApiRetrofit
import app.lawnchair.ui.preferences.navigation.About
import com.android.launcher3.R
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.create

/**
 * Daily update check: every day around [CHECK_TIME] the app looks for a newer build of its
 * [UpdateChannel] and, when there is one it has not announced yet, posts a notification on its own
 * "App updates" channel. Tapping it opens Settings → About, which downloads and installs it.
 */
object FuguetiUpdates {
    private const val TAG = "FuguetiUpdates"

    const val NOTIFICATION_CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1601

    /** Local time of the daily check; the alarm may run up to [CHECK_WINDOW] later. */
    private val CHECK_TIME: LocalTime = LocalTime.of(10, 0)
    private val CHECK_WINDOW: Duration = Duration.ofMinutes(30)
    private val CHECK_INTERVAL: Duration = Duration.ofDays(1)

    private const val STATE_PREFS = "fugueti_updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LAST_NOTIFIED = "last_notified"
    private const val KEY_PERMISSION_ASKED = "notification_permission_asked"

    const val ACTION_CHECK = "app.fugueti.update.CHECK"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether this build updates itself and the user left the automatic check on. */
    fun isEnabled(context: Context): Boolean {
        val prefs2 = PreferenceManager2.getInstance(context)
        return when (UpdateChannel.current) {
            UpdateChannel.Release -> prefs2.autoUpdaterRelease.firstCached()
            UpdateChannel.Nightly -> prefs2.autoUpdaterNightly.firstCached()
            null -> false
        }
    }

    /** Called when the automatic check preference of [channel] changes to [enabled]. */
    fun onPreferenceChanged(context: Context, channel: UpdateChannel, enabled: Boolean) {
        if (channel == UpdateChannel.current) schedule(context, enabled)
    }

    /**
     * Sets the alarm for the next check, or cancels it. Safe to call often: the alarm is replaced.
     * A check missed by more than a day (phone off at [CHECK_TIME]) runs within the next minutes.
     */
    fun schedule(context: Context, enabled: Boolean = isEnabled(context)) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = checkPendingIntent(context)
        if (!enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val now = ZonedDateTime.now()
        val lastCheck = state(context).getLong(KEY_LAST_CHECK, 0L)
        val overdue = lastCheck > 0 && now.toInstant().toEpochMilli() - lastCheck > CHECK_INTERVAL.toMillis()
        val triggerAt = if (overdue) {
            now.plusMinutes(1)
        } else {
            val today = now.with(CHECK_TIME).withSecond(0).withNano(0)
            if (today.isAfter(now)) today else today.plusDays(1)
        }
        alarmManager.setWindow(
            AlarmManager.RTC,
            triggerAt.toInstant().toEpochMilli(),
            CHECK_WINDOW.toMillis(),
            pendingIntent,
        )
        Log.d(TAG, "Next update check at $triggerAt")
    }

    private fun checkPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, UpdateCheckReceiver::class.java).setAction(ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Runs one check in the background, then schedules the next one. */
    fun runCheck(context: Context, onDone: () -> Unit) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                if (isEnabled(appContext)) check(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
            } finally {
                schedule(appContext)
                onDone()
            }
        }
    }

    private suspend fun check(context: Context) {
        val channel = UpdateChannel.current ?: return
        val api: GitHubService = gitHubApiRetrofit.create()
        val result = UpdateChecker(api, channel).check(withChangelog = false)
        state(context).edit { putLong(KEY_LAST_CHECK, System.currentTimeMillis()) }
        if (result !is UpdateCheckResult.Available) return
        val update = result.update
        // Announce each build once; About still offers it afterwards.
        if (state(context).getString(KEY_LAST_NOTIFIED, null) == update.assetName) return
        if (notify(context, update)) {
            state(context).edit { putString(KEY_LAST_NOTIFIED, update.assetName) }
        }
    }

    private fun notify(context: Context, update: FoundUpdate): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false
        createChannel(context)
        val intent = PreferenceActivity.createIntent(context, About)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_new_releases)
            .setContentTitle(context.getString(R.string.update_notification_title, update.label))
            .setContentText(context.getString(R.string.update_notification_text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to post the update notification", e)
            false
        }
    }

    /** Removes the update notification (About shows the update itself). */
    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.update_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Asks once for the notification permission (Android 13+) when the automatic check is on, so
     * the daily check can announce updates. Declining keeps the check working inside About.
     */
    fun maybeRequestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isEnabled(activity)) return
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val state = state(activity)
        if (state.getBoolean(KEY_PERMISSION_ASKED, false)) return
        state.edit { putBoolean(KEY_PERMISSION_ASKED, true) }
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }

    private fun state(context: Context) = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
}

/** Runs the daily check (alarm from [FuguetiUpdates.schedule]); not exported. */
class UpdateCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FuguetiUpdates.ACTION_CHECK) return
        val pending = goAsync()
        FuguetiUpdates.runCheck(context) { pending.finish() }
    }
}

/** Restores the alarm after a reboot, an app update or a clock change (system broadcasts). */
class UpdateScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> FuguetiUpdates.schedule(context)
        }
    }
}
