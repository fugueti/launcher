/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widgetpicker.theme

import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.theme.isSelectedThemeDark
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme

/**
 * Contains theme that launcher applies to the widget picker.
 *
 * Lawnchair: Lawnchair's theme (light/dark preference, accent color and color style) instead of
 * the system's dynamic colors, as the Android 16 picker was themed with Lawnchair's color tokens.
 */
@Composable
fun LauncherWidgetPickerTheme(
    supportsBlurTokens: Boolean = false,
    isBlurEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSelectedThemeDark
    LawnchairTheme(darkTheme = darkTheme) {
        WidgetPickerTheme(
            colors =
                lawnchairWidgetPickerColors(
                    scheme = MaterialTheme.colorScheme,
                    darkTheme = darkTheme,
                    supportsBlurTokens = supportsBlurTokens,
                    isBlurEnabled = isBlurEnabled,
                ),
            textStyles = launcherWidgetPickerTextStyles(),
        ) {
            content()
        }
    }
}
