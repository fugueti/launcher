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

// Part of the tracinglib stand-in (see TracingShim.kt): no tracing, same API.
package com.android.app.tracing

import kotlinx.coroutines.flow.Flow

object FlowTracing {
    @Suppress("UNUSED_PARAMETER")
    inline fun <T> Flow<T>.traceEach(
        flowName: String,
        logcat: Boolean = false,
        traceEmissionCount: Boolean = false,
        crossinline valueToString: (T) -> String = { it.toString() },
    ): Flow<T> = this
}
