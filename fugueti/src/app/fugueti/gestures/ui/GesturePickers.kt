/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fugueti.gestures.actions.IntentUris
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ends the picker activity, handing the chosen config back to the gesture settings. */
private fun Activity.finishWith(config: GestureHandlerConfig) {
    setResult(Activity.RESULT_OK, Intent().putExtra("config", kotlinxJson.encodeToString(config)))
    finish()
}

/** A simple text row used by the second level of the pickers. */
@Composable
private fun TextRow(title: String, subtitle: String?, onClick: () -> Unit) {
    PreferenceTemplate(
        title = { Text(title) },
        description = { if (subtitle != null) Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Two level picker: first the installed apps, then the [Item]s of the chosen app produced by
 * [loadItems] (shortcuts or activities).
 */
@Composable
private fun <Item> AppThenItemPicker(
    title: String,
    emptyText: String,
    loadItems: suspend (App) -> List<Item>,
    itemTitle: (Item) -> String,
    itemSubtitle: (Item) -> String?,
    onPick: (App, Item) -> Unit,
) {
    val apps by appsState()
    var selected by remember { mutableStateOf<App?>(null) }
    var items by remember { mutableStateOf<List<Item>?>(null) }
    val currentLoadItems by rememberUpdatedState(loadItems)

    BackHandler(enabled = selected != null) { selected = null }
    LaunchedEffect(selected) {
        val app = selected
        items = null
        if (app != null) items = withContext(Dispatchers.IO) { currentLoadItems(app) }
    }

    PreferenceScaffold(
        label = selected?.label ?: title,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { padding ->
        val app = selected
        if (app == null) {
            PreferenceLazyColumn(padding) {
                preferenceGroupItems(items = apps, isFirstChild = true) { _, a ->
                    AppItem(app = a, onClick = { selected = it })
                }
            }
        } else {
            val list = items
            LazyColumn(modifier = Modifier.padding(padding)) {
                when {
                    list == null -> Unit

                    list.isEmpty() -> item { TextRow(emptyText, null) { selected = null } }

                    else -> items(list) { item ->
                        TextRow(itemTitle(item), itemSubtitle(item)) { onPick(app, item) }
                    }
                }
            }
        }
    }
}

@Composable
fun PickShortcutForGesture() {
    val activity = LocalContext.current as Activity
    val launcherApps = activity.getSystemService(LauncherApps::class.java)
    AppThenItemPicker<ShortcutInfo>(
        title = stringResource(R.string.fg_pick_shortcut),
        emptyText = stringResource(R.string.fg_no_shortcuts),
        loadItems = { app ->
            runCatching {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(app.key.componentName.packageName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
                launcherApps?.getShortcuts(query, app.key.user).orEmpty()
                    .distinctBy { it.id }
                    .sortedBy { it.shortLabel?.toString().orEmpty() }
            }.getOrDefault(emptyList())
        },
        itemTitle = { (it.longLabel ?: it.shortLabel ?: it.id).toString() },
        itemSubtitle = { null },
        onPick = { app, shortcut ->
            activity.finishWith(
                GestureHandlerConfig.OpenApp(
                    appName = "${app.label}: ${shortcut.shortLabel ?: shortcut.id}",
                    target = OpenAppTarget.Shortcut(
                        intent = Intent(),
                        user = app.key.user,
                        packageName = shortcut.`package`,
                        id = shortcut.id,
                    ),
                ),
            )
        },
    )
}

@Composable
fun PickActivityForGesture() {
    val activity = LocalContext.current as Activity
    val pm = activity.packageManager
    AppThenItemPicker<ActivityInfo>(
        title = stringResource(R.string.fg_pick_activity),
        emptyText = stringResource(R.string.fg_no_activities),
        loadItems = { app ->
            val pkg = app.key.componentName.packageName
            runCatching {
                val info = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                }
                // Only the main profile can be started directly with startActivity.
                if (app.key.user != Process.myUserHandle()) return@runCatching emptyList()
                info.activities.orEmpty()
                    .filter { it.exported && it.enabled && it.permission == null }
                    .sortedBy { it.name }
            }.getOrDefault(emptyList())
        },
        itemTitle = { it.loadLabel(pm).toString() },
        itemSubtitle = { it.name.removePrefix(it.packageName) },
        onPick = { app, info ->
            activity.finishWith(
                GestureHandlerConfig.OpenActivity(
                    label = "${app.label}: ${info.loadLabel(pm)}",
                    packageName = info.packageName,
                    className = info.name,
                ),
            )
        },
    )
}

@Composable
fun EnterIntentForGesture(
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current as Activity
    var label by rememberSaveable { mutableStateOf("") }
    var uri by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    val invalidText = stringResource(R.string.fg_intent_invalid)

    fun validate(): Boolean {
        error = !IntentUris.canResolve(activity, uri)
        return !error
    }

    PreferenceLayout(label = stringResource(R.string.fg_intent_title), modifier = modifier) {
        PreferenceGroup {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.fg_intent_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uri,
                    onValueChange = {
                        uri = it
                        error = false
                    },
                    label = { Text(stringResource(R.string.fg_intent_uri)) },
                    isError = error,
                    supportingText = { Text(if (error) invalidText else stringResource(R.string.fg_intent_help)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        if (validate()) {
                            runCatching { activity.startActivity(IntentUris.parse(uri)) }
                                .onFailure { Toast.makeText(activity, invalidText, Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text(stringResource(R.string.fg_intent_try)) }
                    Button(
                        enabled = uri.isNotBlank(),
                        onClick = {
                            if (validate()) {
                                activity.finishWith(
                                    GestureHandlerConfig.OpenIntent(
                                        label = label.ifBlank { uri.trim().take(40) },
                                        uri = uri.trim(),
                                    ),
                                )
                            }
                        },
                    ) { Text(stringResource(R.string.fg_intent_save)) }
                }
            }
        }
    }
}
