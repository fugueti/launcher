/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * These handlers live in the upstream package because GestureHandler is a sealed class.
 */

package app.lawnchair.gestures.handlers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.fugueti.gestures.actions.ActionErrors
import app.fugueti.gestures.actions.IntentUris
import app.lawnchair.LawnchairLauncher

/** Opens one specific (exported) activity, chosen in the activity picker. */
class OpenActivityGestureHandler(
    context: Context,
    private val packageName: String,
    private val className: String,
) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(packageName, className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { launcher.startActivity(intent) }
            .onFailure { ActionErrors.report(launcher, it) }
    }
}

/** Launches an intent described by a URI typed by the user. */
class OpenIntentGestureHandler(
    context: Context,
    private val uri: String,
) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        runCatching { launcher.startActivity(IntentUris.parse(uri)) }
            .onFailure { ActionErrors.report(launcher, it) }
    }
}
