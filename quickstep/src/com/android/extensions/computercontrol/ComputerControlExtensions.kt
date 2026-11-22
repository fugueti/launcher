/*
 * Copyright (C) 2026 Fugueti Launcher contributors
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

// Fugueti: stand-in for the com.android.extensions.computercontrol system library, which only
// privileged/system launchers can load. getInstance() returns null, so the automation
// indicator in AutomationRepositoryImpl stays off.
package com.android.extensions.computercontrol

import android.content.Context
import java.util.concurrent.Executor

class ComputerControlExtensions private constructor() {

    @Suppress("UNUSED_PARAMETER")
    fun registerAutomatedPackageListener(
        context: Context,
        executor: Executor,
        listener: AutomatedPackageListener,
    ) {}

    @Suppress("UNUSED_PARAMETER")
    fun unregisterAutomatedPackageListener(context: Context, listener: AutomatedPackageListener) {}

    companion object {
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun getInstance(context: Context): ComputerControlExtensions? = null
    }
}

/** Fugueti: listener shape of the system library (automator package, automated packages, user). */
class AutomatedPackageListener(
    val onChange: (automator: String, automatedPackages: List<String>, user: android.os.UserHandle) -> Unit
)
