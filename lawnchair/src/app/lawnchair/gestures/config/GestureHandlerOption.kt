// Modified by Fugueti Launcher contributors (2026): see the "Fugueti" comments below.
package app.lawnchair.gestures.config

import android.app.Activity
import android.content.Context
import app.lawnchair.BlankActivity
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.GesturesEnterIntent
import app.lawnchair.ui.preferences.navigation.GesturesPickActivity
import app.lawnchair.ui.preferences.navigation.GesturesPickApp
import app.lawnchair.ui.preferences.navigation.GesturesPickShortcut
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.R

sealed class GestureHandlerOption(
    private val labelRes: Int,
    val iconRes: Int,
    val configClass: Class<*>,
) {
    fun getLabel(context: Context) = context.getString(labelRes)

    /** Fugueti: whether [config] was produced by this option (drives the radio button). */
    open fun matches(config: GestureHandlerConfig): Boolean = config::class.java == configClass

    abstract suspend fun buildConfig(activity: Activity): GestureHandlerConfig?

    sealed class Simple(labelRes: Int, iconRes: Int, val obj: GestureHandlerConfig) : GestureHandlerOption(labelRes, iconRes, obj::class.java) {
        constructor(obj: GestureHandlerConfig.Simple) : this(obj.labelRes, obj.iconRes, obj)
        override suspend fun buildConfig(activity: Activity) = obj
    }

    data object NoOp : Simple(GestureHandlerConfig.NoOp)
    data object Sleep : Simple(GestureHandlerConfig.Sleep)
    data object Recents : Simple(GestureHandlerConfig.Recents)
    data object OpenNotifications : Simple(GestureHandlerConfig.OpenNotifications)
    data object OpenQuickSettings : Simple(GestureHandlerConfig.OpenQuickSettings)
    data object OpenAppDrawer : Simple(GestureHandlerConfig.OpenAppDrawer)
    data object OpenAppSearch : Simple(GestureHandlerConfig.OpenAppSearch)
    data object OpenSearch : Simple(GestureHandlerConfig.OpenSearch)
    data object OpenAssistant : Simple(GestureHandlerConfig.OpenAssistant)

    data object OpenApp : GestureHandlerOption(
        R.string.gesture_handler_open_app_option,
        R.drawable.ic_launcher_home,
        GestureHandlerConfig.OpenApp::class.java,
    ) {
        override fun matches(config: GestureHandlerConfig) = config is GestureHandlerConfig.OpenApp && config.target is OpenAppTarget.App

        override suspend fun buildConfig(activity: Activity) = pickConfig(activity, GesturesPickApp)
    }

    // region Fugueti options

    data object OpenShortcut : GestureHandlerOption(
        R.string.fg_action_open_shortcut_option,
        R.drawable.ic_launcher_home,
        GestureHandlerConfig.OpenApp::class.java,
    ) {
        override fun matches(config: GestureHandlerConfig) = config is GestureHandlerConfig.OpenApp && config.target is OpenAppTarget.Shortcut

        override suspend fun buildConfig(activity: Activity) = pickConfig(activity, GesturesPickShortcut)
    }

    data object OpenActivity : GestureHandlerOption(
        R.string.fg_action_open_activity_option,
        R.drawable.ic_apps,
        GestureHandlerConfig.OpenActivity::class.java,
    ) {
        override suspend fun buildConfig(activity: Activity) = pickConfig(activity, GesturesPickActivity)
    }

    data object OpenIntent : GestureHandlerOption(
        R.string.fg_action_open_intent_option,
        R.drawable.ic_open_in_store,
        GestureHandlerConfig.OpenIntent::class.java,
    ) {
        override suspend fun buildConfig(activity: Activity) = pickConfig(activity, GesturesEnterIntent)
    }

    // endregion
}

/** Opens a picker screen of the settings activity and decodes the config it returns. */
private suspend fun pickConfig(activity: Activity, route: PreferenceRoute): GestureHandlerConfig? {
    val intent = PreferenceActivity.createIntent(activity, route)
    val result = BlankActivity.startBlankActivityForResult(activity, intent)
    val configString = result.data?.getStringExtra("config") ?: return null
    return kotlinxJson.decodeFromString(configString)
}
