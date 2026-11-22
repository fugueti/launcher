/*
 * Copyright (C) 2026 Fugueti
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

package com.android.launcher3.util

/**
 * Fugueti: platform aconfig flags that AOSP Launcher3 reads but that live in the framework's
 * hidden_from_bootclasspath packages, so a non-system launcher cannot read them. The features
 * behind them (task continuity/handoff, app lock, MediaProvider trash by path) need system
 * privileges, so they stay off.
 */
object HiddenPlatformFlags {
    /** android.companion.Flags.taskContinuity */
    @JvmStatic fun taskContinuity() = false

    /** android.security.Flags.appLockCore */
    @JvmStatic fun appLockCore() = false

    /** android.security.Flags.appLockApis */
    @JvmStatic fun appLockApis() = false

    /** com.android.server.ui_latency_stats.Flags.uiLatencyStatsService */
    @JvmStatic fun uiLatencyStatsService() = false

    /** com.android.providers.media.flags.Flags.enableTrashAndRestoreByFilePathApi */
    @JvmStatic fun enableTrashAndRestoreByFilePathApi() = false
}
