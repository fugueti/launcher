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
package com.android.app.tracing.coroutines

import android.os.Trace
import com.android.app.tracing.traceSection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
fun CoroutineScope.launchTraced(
    spanName: String? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context, start, block)

@Suppress("UNUSED_PARAMETER")
inline fun CoroutineScope.launchTraced(
    crossinline spanName: () -> String,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    noinline block: suspend CoroutineScope.() -> Unit,
): Job = launch(context, start, block)

/** Track tracer without async tracks: slices are plain sync sections. */
@Suppress("UNUSED_PARAMETER")
class TrackTracer(
    val trackName: String,
    val traceTag: Long = 0L,
    val trackGroup: String? = null,
) {
    inline fun <T> traceSyncAndAsync(sliceName: () -> String, block: () -> T): T =
        if (Trace.isEnabled()) traceSection(sliceName(), block) else block()

    inline fun <T> traceAsync(sliceName: String, block: () -> T): T =
        traceSection(sliceName, block)

    inline fun <T> traceAsync(sliceName: () -> String, block: () -> T): T =
        if (Trace.isEnabled()) traceSection(sliceName(), block) else block()

    fun instant(s: () -> String) {}
}
