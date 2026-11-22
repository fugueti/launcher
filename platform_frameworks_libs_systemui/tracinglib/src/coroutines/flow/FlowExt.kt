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
package com.android.app.tracing.coroutines.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Suppress("UNUSED_PARAMETER")
fun <T> Flow<T>.stateInTraced(
    name: String,
    scope: CoroutineScope,
    started: SharingStarted,
    initialValue: T,
): StateFlow<T> = stateIn(scope, started, initialValue)

@Suppress("UNUSED_PARAMETER")
suspend fun <T> Flow<T>.stateInTraced(name: String, scope: CoroutineScope): StateFlow<T> =
    stateIn(scope)
