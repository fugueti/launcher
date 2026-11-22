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

// Minimal stand-in for frameworks/libs/systemui/tracinglib, which is not vendored. It keeps the
// AOSP call sites unchanged and only uses public android.os.Trace APIs.
package com.android.app.tracing

import android.os.Trace
import android.util.Log



/** Runs [block] inside a systrace section named [sectionName]. */
inline fun <T> traceSection(sectionName: String, block: () -> T): T {
    Trace.beginSection(sectionName.take(127))
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}

/** Runs [block] inside a systrace section whose name is computed lazily. */
inline fun <T> traceSection(sectionName: () -> String, block: () -> T): T =
    traceSection(sectionName(), block)

object TraceUtils {
    @JvmStatic
    inline fun <T> trace(sectionName: String, block: () -> T): T = traceSection(sectionName, block)
}

/** Logs state changes of a named track; here it only writes to logcat when asked to. */
class TraceStateLogger
@JvmOverloads
constructor(
    private val trackName: String,
    private val logOnlyIfDifferent: Boolean = true,
    private val instantEvent: Boolean = true,
    private val logcat: Boolean = false,
) {
    private var previousValue: String? = null

    fun log(newValue: String) {
        if (logOnlyIfDifferent && newValue == previousValue) return
        previousValue = newValue
        if (logcat) Log.d("TraceStateLogger", "$trackName: $newValue")
    }
}
