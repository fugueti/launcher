/*
 * Modified by Fugueti Launcher contributors (2026): the screen now lists the 13 home screen
 * gestures grouped by kind, plus haptics, sensitivity and the gesture test pad.
 * Modifications are distributed under GPL-3.0-or-later.
 */

package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.fugueti.gestures.GestureLabels
import app.fugueti.gestures.engine.GestureKind
import app.fugueti.gestures.engine.Sensitivity
import app.lawnchair.gestures.handlers.SleepMode
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.GestureHandlerPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GesturesTestPad
import com.android.launcher3.R

@Composable
fun GesturePreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager2()
    val context = LocalContext.current
    val navController = LocalNavController.current
    PreferenceLayout(
        label = stringResource(id = R.string.gestures_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        GestureKind.Group.entries.forEach { group ->
            PreferenceGroup(heading = stringResource(GestureLabels.groupRes(group))) {
                GestureKind.entries.filter { it.group == group }.forEach { kind ->
                    val hint = GestureLabels.hintRes(kind)
                    GestureHandlerPreference(
                        adapter = prefs.gestureHandler(kind).getAdapter(),
                        label = stringResource(GestureLabels.labelRes(kind)),
                        hint = if (hint != 0) context.getString(hint) else null,
                    )
                }
            }
        }
        PreferenceGroup(heading = stringResource(R.string.fg_group_buttons)) {
            GestureHandlerPreference(
                adapter = prefs.homePressGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_home_tap),
            )
            GestureHandlerPreference(
                adapter = prefs.backPressGestureHandler.getAdapter(),
                label = stringResource(id = R.string.gesture_back_tap),
            )
        }
        PreferenceGroup(heading = stringResource(id = R.string.sleep_mode_label)) {
            ListPreference(
                adapter = prefs.sleepMode.getAdapter(),
                entries = SleepMode.entries(),
                label = stringResource(id = R.string.sleep_mode_label),
            )
        }
        PreferenceGroup(heading = stringResource(R.string.fg_group_options)) {
            SwitchPreference(
                adapter = prefs.gestureHaptics.getAdapter(),
                label = stringResource(R.string.fg_haptics_label),
                description = stringResource(R.string.fg_haptics_description),
            )
            ListPreference(
                adapter = prefs.gestureSensitivity.getAdapter(),
                label = stringResource(R.string.fg_sensitivity_label),
                entries = listOf(
                    ListPreferenceEntry(Sensitivity.LOW.name) { stringResource(R.string.fg_sensitivity_low) },
                    ListPreferenceEntry(Sensitivity.MEDIUM.name) { stringResource(R.string.fg_sensitivity_medium) },
                    ListPreferenceEntry(Sensitivity.HIGH.name) { stringResource(R.string.fg_sensitivity_high) },
                ),
            )
            ClickablePreference(
                label = stringResource(R.string.fg_test_pad_label),
                subtitle = stringResource(R.string.fg_test_pad_description),
                onClick = { navController.navigate(GesturesTestPad) },
            )
        }
    }
}
