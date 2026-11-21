/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.nova

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import app.fugueti.gestures.engine.GestureKind
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.util.ComponentKey
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the home screen gestures of a Nova Launcher backup (`nova.xml`) and turns them into
 * Fugueti gesture actions.
 *
 * Nova stores each gesture as `<string name="gesture_…">Label$#Intent;…;end</string>`: either a
 * Nova action (`S.LAUNCHER_ACTION=APP_DRAWER`, `SCREEN_OFF`…), an app launch (MAIN/LAUNCHER intent)
 * or any other intent. Nova actions without a Fugueti equivalent are skipped, so those gestures keep
 * their current action.
 */
object NovaGestureImporter {
    private const val TAG = "NovaGestureImporter"
    private const val NOVA_PACKAGE = "com.teslacoilsw.launcher"
    private const val NOVA_ACTION_EXTRA = "LAUNCHER_ACTION"
    private const val LABEL_SEPARATOR = "$#"

    /** Nova gesture key → Fugueti gesture. */
    private val GESTURES = mapOf(
        "gesture_swipe_up" to GestureKind.SWIPE_UP,
        "gesture_swipe_down" to GestureKind.SWIPE_DOWN,
        "gesture_double_tap" to GestureKind.DOUBLE_TAP,
        "gesture_double_tap_swipe_up" to GestureKind.DOUBLE_TAP_SWIPE_UP,
        "gesture_double_tap_swipe_down" to GestureKind.DOUBLE_TAP_SWIPE_DOWN,
        "gesture_two_finger_scroll_up" to GestureKind.TWO_FINGER_SWIPE_UP,
        "gesture_two_finger_scroll_down" to GestureKind.TWO_FINGER_SWIPE_DOWN,
        "gesture_pinch" to GestureKind.PINCH_IN,
        "gesture_pinch_in" to GestureKind.PINCH_IN,
        "gesture_pinch_out" to GestureKind.PINCH_OUT,
        "gesture_rotate_cw" to GestureKind.ROTATE_CW,
        "gesture_rotate_ccw" to GestureKind.ROTATE_CCW,
    )

    /** Parses [novaXml]; returns the Fugueti action of every gesture that can be imported. */
    fun parse(novaXml: File): Map<GestureKind, GestureHandlerConfig> {
        val result = linkedMapOf<GestureKind, GestureHandlerConfig>()
        val root = runCatching {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(novaXml).documentElement
        }.getOrElse {
            Log.w(TAG, "Unreadable nova.xml", it)
            return result
        }
        val strings = root.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val node = strings.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
            val kind = GESTURES[name] ?: continue
            val config = toConfig(node.textContent ?: continue)
            if (config == null) {
                Log.i(TAG, "Skipping $name: no Fugueti equivalent")
                continue
            }
            result[kind] = config
        }
        return result
    }

    /** Writes [gestures] to Fugueti's gesture settings. */
    suspend fun apply(context: Context, gestures: Map<GestureKind, GestureHandlerConfig>) {
        val prefs = PreferenceManager2.getInstance(context)
        gestures.forEach { (kind, config) -> prefs.gestureHandler(kind).set(config) }
    }

    /** `Label$#Intent;…;end` → Fugueti action, or null when there is no equivalent. */
    internal fun toConfig(value: String): GestureHandlerConfig? {
        val separator = value.indexOf(LABEL_SEPARATOR)
        if (separator < 0) return null
        val label = value.substring(0, separator).trim()
        val intent = runCatching { Intent.parseUri(value.substring(separator + 1), 0) }.getOrNull()
            ?: return null
        intent.sourceBounds = null

        val component = intent.component
        if (component?.packageName == NOVA_PACKAGE || intent.action == "$NOVA_PACKAGE.ACTION") {
            return novaAction(intent.getStringExtra(NOVA_ACTION_EXTRA))
        }
        if (component != null && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        ) {
            val key = ComponentKey(component, Process.myUserHandle())
            return GestureHandlerConfig.OpenApp(label.ifEmpty { component.packageName }, OpenAppTarget.App(key))
        }
        return GestureHandlerConfig.OpenIntent(
            label.ifEmpty { intent.`package` ?: component?.packageName ?: "Intent" },
            intent.toUri(Intent.URI_INTENT_SCHEME),
        )
    }

    /** Nova's own actions (`S.LAUNCHER_ACTION=…`). */
    private fun novaAction(action: String?): GestureHandlerConfig? {
        val a = action?.uppercase() ?: return null
        return when {
            a == "NONE" || a == "NOTHING" -> GestureHandlerConfig.NoOp
            a == "APP_DRAWER" || a == "OPEN_DRAWER" -> GestureHandlerConfig.OpenAppDrawer
            "APP_SEARCH" in a || "DRAWER_SEARCH" in a -> GestureHandlerConfig.OpenAppSearch
            "SCREEN_OFF" in a || "SCREEN_LOCK" in a || "LOCK_SCREEN" in a -> GestureHandlerConfig.Sleep
            "QUICK_SETTINGS" in a || "QUICKSETTINGS" in a || "EXPAND_SETTINGS" in a -> GestureHandlerConfig.OpenQuickSettings
            "STATUS_BAR" in a || "NOTIFICATION" in a -> GestureHandlerConfig.OpenNotifications
            "RECENT" in a -> GestureHandlerConfig.Recents
            "VOICE" in a || "ASSIST" in a -> GestureHandlerConfig.OpenAssistant
            "SEARCH" in a -> GestureHandlerConfig.OpenSearch
            else -> null
        }
    }
}
