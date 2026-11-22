package app.lawnchair.icons

import android.content.Context
import android.util.Log
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.preferences.PreferenceChangeListener
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.ThemeProvider
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.display.OverlayChangeHandler
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.graphics.ThemeManager.Companion.DEFAULT_SHAPE_DELEGATE
import com.android.launcher3.graphics.ThemeManager.Companion.ICON_FACTORY_DAGGER_KEY
import com.android.launcher3.graphics.theme.IconThemeFactory
import com.android.launcher3.graphics.theme.ThemePreference
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.icons.mono.ThemedIconDelegate
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.display.DisplayController
import com.android.launcher3.display.LauncherDisplayInfo.Companion.CHANGE_NIGHT_MODE
import com.android.launcher3.util.LooperExecutor
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus

@LauncherAppSingleton
class LawnchairThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    private val prefs: LauncherPrefs,
    themePreference: ThemePreference,
    @Named(ICON_FACTORY_DAGGER_KEY)
    iconThemeFactories: Map<String, @JvmSuppressWildcards IconThemeFactory>,
    overlayChangeHandler: OverlayChangeHandler,
    private val lifecycle: DaggerSingletonTracker,
    private val prefs2: PreferenceManager2,
    private val prefs1: PreferenceManager,
    private val themeProvider: ThemeProvider,
) : ThemeManager(
    context,
    prefs,
    themePreference,
    iconThemeFactories,
    uiExecutor,
    overlayChangeHandler,
    lifecycle,
) {
    private val statePrefs1 = listOf(
        prefs1.wrapAdaptiveIcons,
        prefs1.transparentIconBackground,
        prefs1.shadowBGIcons,
        prefs1.coloredBackgroundLightness,
        prefs1.forceIconMonochrome,
        prefs1.launcherTheme,
    )

    private val prefListener = PreferenceChangeListener {
        uiExecutor.execute { verifyIconState() }
    }

    private val themedIconsListener = PreferenceChangeListener {
        uiExecutor.execute { syncMonoThemeFromLawnchair() }
    }

    // Set once this class is constructed: ThemeManager's constructor can call verifyIconState
    // (overlay/theme callbacks) before the fields below exist.
    @Volatile
    private var initialized = false

    init {
        // Lawnchair: replace the AOSP icon state. Not an override of iconState: the base
        // constructor reads it before a subclass backing field would be assigned.
        iconState = parseIconStateV2(iconState)
        val scope = MainScope() + CoroutineName("LawnchairThemeManager")
        merge(
            prefs2.iconShape.get(),
            prefs2.customIconShape.get(),
            prefs2.folderShape.get(),
            prefs2.customFolderShape.get(),
        ).onEach { verifyIconState() }
            .launchIn(scope)

        val colorSchemeChangeListener = object : ThemeProvider.ColorSchemeChangeListener {
            @Volatile
            private var isDestroyed = false

            override fun onColorSchemeChanged() {
                synchronized(this) {
                    if (isDestroyed) return
                    uiExecutor.execute {
                        synchronized(this) {
                            if (isDestroyed) return@execute
                            verifyIconState()
                        }
                    }
                }
            }

            fun destroy() {
                synchronized(this) {
                    isDestroyed = true
                    themeProvider.removeListener(this)
                }
            }
        }
        themeProvider.addListener(colorSchemeChangeListener)

        val nightModeCloseable = DisplayController.INSTANCE.get(context).listenable
            ?.changes
            ?.forEach(uiExecutor) { flags ->
                if (flags and CHANGE_NIGHT_MODE != 0) verifyIconState()
            }

        statePrefs1.forEach { it.addListener(prefListener) }

        // Lawnchair's "Themed icons" (key themed_icons) and AOSP 17's theme preference (also set
        // by the wallpaper picker) are one setting, as they shared the themed_icons key on Android
        // 16. Lawnchair's value wins at start; without one (ThemePreference used to delete it),
        // it is restored from the AOSP preference. Posted: writing Lawnchair's preference reloads
        // the icons, which need this (still constructing) singleton.
        uiExecutor.execute {
            if (prefs1.sp.contains(prefs1.themedIcons.key)) {
                syncMonoThemeFromLawnchair()
            } else if (isMonoThemeEnabled) {
                prefs1.themedIcons.set(true)
            }
        }
        prefs1.themedIcons.addListener(themedIconsListener)
        // The AOSP preference changed (e.g. the wallpaper picker): mirror it in Lawnchair's.
        val monoThemeCloseable = themePreference.forEach(uiExecutor) {
            val mono = isMonoThemeEnabled
            if (prefs1.themedIcons.get() != mono) prefs1.themedIcons.set(mono)
        }

        initialized = true

        lifecycle.addCloseable {
            colorSchemeChangeListener.destroy()
            scope.cancel()
            nightModeCloseable?.close()
            statePrefs1.forEach { it.removeListener(prefListener) }
            prefs1.themedIcons.removeListener(themedIconsListener)
            monoThemeCloseable.close()
        }
    }

    /** Lawnchair's setting changed: update the AOSP preference, which refreshes the icons. */
    private fun syncMonoThemeFromLawnchair() {
        val themed = prefs1.themedIcons.get()
        if (isMonoThemeEnabled != themed) isMonoThemeEnabled = themed
    }

    override fun verifyIconState() {
        if (!initialized) return
        val newState = parseIconStateV2(iconState)
        if (newState == iconState) return
        iconState = newState

        listeners.forEach { it.onThemeChanged() }
    }

    private fun prefs1State(): String = statePrefs1.joinToString(",") { it.get().toString() }

    private fun parseIconStateV2(oldState: IconState?): IconState {
        val currentAppShape: IconShape = try {
            prefs2.iconShape.firstCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting icon shape", e)
            IconShape.Circle
        }

        val currentFolderShape: IconShape = try {
            prefs2.folderShape.firstCached()
        } catch (e: Exception) {
            Log.d(TAG, "Error getting folder shape", e)
            IconShape.Circle
        }

        val currentPrefs1State = prefs1State()
        val appShapeKey = currentAppShape.getHashString() + currentPrefs1State
        val folderShapeKey = currentFolderShape.getHashString() + currentPrefs1State
        // Lawnchair: themed icons when the mono theme is selected (Lawnchair's themed icons
        // preference writes it); forced monochrome uses its own controller
        val themeController =
            if (isMonoThemeEnabled) {
                if (prefs1.forceIconMonochrome.get()) {
                    FORCED_MONO_THEME_CONTROLLER
                } else {
                    MONO_THEME_CONTROLLER
                }
            } else {
                null
            }
        val themedColorsKey =
            if (themeController != null) {
                ThemedIconDelegate.getColorArray(context).joinToString(",")
            } else {
                ""
            }
        val combinedKey = "$appShapeKey:$folderShapeKey:$themedColorsKey"

        val appShape =
            if (oldState != null && (oldState.iconShape as? PathShapeDelegate)?.iconShape == currentAppShape) {
                oldState.iconShape
            } else {
                PathShapeDelegate(currentAppShape)
            }

        val folderShape =
            if (oldState != null && (oldState.folderShape as? PathShapeDelegate)?.iconShape == currentFolderShape) {
                oldState.folderShape
            } else {
                PathShapeDelegate(currentFolderShape)
            }

        return IconState(
            iconMask = combinedKey,
            folderRadius = 1f,
            shapeRadius = 1f,
            themeController = themeController,
            themeCode = if (themeController != null) "lawnchair-mono:$combinedKey" else "no-theme",
            iconShape = appShape,
            folderShape = folderShape,
            fileShape = oldState?.fileShape ?: DEFAULT_SHAPE_DELEGATE,
        )
    }
}

// Reuse controllers to allow the equality check in verifyIconState.
private val MONO_THEME_CONTROLLER = MonoIconThemeController()
private val FORCED_MONO_THEME_CONTROLLER = MonoIconThemeController(shouldForceThemeIcon = true)
private const val TAG = "LawnchairThemeManager"
