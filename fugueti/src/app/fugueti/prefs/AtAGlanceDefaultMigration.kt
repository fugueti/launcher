/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.prefs

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * At a Glance is on by default since 16.0.5 (like Lawnchair); until then it was off. A default is
 * not stored, so without this every install that never touched it would get At a Glance after the
 * update, and turning it on drops the items in the first row of the first page.
 *
 * Runs once per settings store: an install that existed before (the app was updated, not freshly
 * installed) and never set At a Glance keeps it off. [MIGRATED] marks the store as done, so it is
 * in every backup made from then on; restoring an older Fugueti backup applies the same rule
 * (BackupStore.applyBackupDefaults).
 */
class AtAGlanceDefaultMigration(private val context: Context) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[MIGRATED] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        if (ENABLED !in prefs && wasUpdated()) prefs[ENABLED] = false
        prefs[MIGRATED] = true
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    private fun wasUpdated(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime != info.firstInstallTime
    }.getOrDefault(false)

    companion object {
        val ENABLED = booleanPreferencesKey("enable_smartspace")
        val MIGRATED = booleanPreferencesKey("fugueti_at_a_glance_default_migrated")
    }
}
