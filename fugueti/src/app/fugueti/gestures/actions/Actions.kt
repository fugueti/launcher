/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.android.launcher3.R
import java.net.URISyntaxException

object ActionErrors {
    private const val TAG = "FuguetiGestures"

    /** Logs a failed action and tells the user in one short toast. */
    fun report(context: Context, error: Throwable) {
        Log.w(TAG, "Gesture action failed", error)
        val message = when (error) {
            is ActivityNotFoundException -> R.string.fg_error_no_activity
            is SecurityException -> R.string.fg_error_not_allowed
            is URISyntaxException -> R.string.fg_error_bad_intent
            else -> R.string.fg_error_generic
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

object IntentUris {

    /**
     * Parses the intent URI typed by the user. Accepts the `intent:#Intent;...;end` format and
     * plain URIs such as `https://example.com` or `tel:123`, which become ACTION_VIEW intents.
     * The selector is dropped and the intent always starts a new task.
     */
    @Throws(URISyntaxException::class)
    fun parse(uri: String): Intent {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) throw URISyntaxException(uri, "empty")
        val intent = Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME or Intent.URI_ANDROID_APP_SCHEME)
        intent.selector = null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /** True when [uri] parses and some installed activity can handle it. */
    fun canResolve(context: Context, uri: String): Boolean = runCatching {
        parse(uri).resolveActivity(context.packageManager) != null
    }.getOrDefault(false)
}
