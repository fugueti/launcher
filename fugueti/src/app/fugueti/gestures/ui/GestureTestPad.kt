/*
 * Fugueti Launcher, an Android launcher based on Lawnchair.
 * Copyright (C) 2026 Fugueti Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.fugueti.gestures.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.fugueti.gestures.GestureLabels
import app.fugueti.gestures.engine.GestureKind
import app.fugueti.gestures.engine.GestureRecognizer
import app.fugueti.gestures.engine.MotionEventAdapter
import app.fugueti.gestures.engine.RecognizerConfig
import app.fugueti.gestures.engine.Sensitivity
import app.fugueti.gestures.engine.TouchAction
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.util.firstBlocking
import com.android.launcher3.R

/**
 * A plain View running the same recognizer as the home screen. It never runs actions, it only
 * reports what it recognized; finger trails are drawn so screenshots show the movement.
 */
class GestureTestPadView(context: Context) : View(context) {

    var onGesture: ((GestureKind?) -> Unit)? = null

    /** Last result, kept for the Espresso tests. Null until a gesture ends. */
    var lastGesture: GestureKind? = null
        private set

    /** Number of touches that ended (recognized or not), for the Espresso tests. */
    var completedTouches = 0
        private set

    val recognizer: GestureRecognizer

    private val trails = mutableMapOf<Int, Path>()
    private val finished = mutableListOf<Path>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var recognizedThisTouch = false

    init {
        tag = TAG
        val prefs = PreferenceManager2.getInstance(context)
        val sensitivity = Sensitivity.fromName(prefs.gestureSensitivity.get().firstBlocking())
        val vc = ViewConfiguration.get(context)
        recognizer = GestureRecognizer(
            RecognizerConfig.forDensity(
                density = resources.displayMetrics.density,
                sensitivity = sensitivity,
                touchSlopPx = vc.scaledTouchSlop.toFloat(),
                doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
                doubleTapSlopPx = vc.scaledDoubleTapSlop.toFloat(),
                maxTapDurationMs = ViewConfiguration.getLongPressTimeout().toLong(),
            ),
        )
    }

    fun setTrailColor(color: Int) {
        paint.color = color
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sample = MotionEventAdapter.toSample(event) ?: return true
        if (sample.action == TouchAction.DOWN) {
            if (!recognizer.isWaitingForSecondTap(sample.timeMs)) finished.clear()
            recognizedThisTouch = false
        }
        for (p in sample.pointers) {
            val path = trails.getOrPut(p.id) { Path().apply { moveTo(p.x, p.y) } }
            path.lineTo(p.x, p.y)
        }
        if (sample.action == TouchAction.DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        val result = recognizer.onTouch(sample)
        if (android.util.Log.isLoggable("Fugueti", android.util.Log.DEBUG)) {
            android.util.Log.d("Fugueti", "pad ${sample.action} t=${sample.timeMs} p=${sample.pointers} phase=${recognizer.phase} result=$result")
        }
        if (result != null) {
            recognizedThisTouch = true
            report(result)
        }
        if (sample.action == TouchAction.UP || sample.action == TouchAction.CANCEL) {
            finished += trails.values
            trails.clear()
            completedTouches++
            // A lone tap may become a double tap: only report "not recognized" when it cannot.
            if (!recognizedThisTouch && !recognizer.isWaitingForSecondTap(sample.timeMs)) report(null)
        }
        invalidate()
        return true
    }

    private fun report(kind: GestureKind?) {
        lastGesture = kind
        onGesture?.invoke(kind)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        finished.forEach { canvas.drawPath(it, paint) }
        trails.values.forEach { canvas.drawPath(it, paint) }
    }

    companion object {
        const val TAG = "fg_test_pad"
    }
}

@Composable
fun GestureTestPadScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager2.getInstance(context) }
    var result by remember { mutableStateOf<String?>(null) }
    val noneText = stringResource(R.string.fg_test_pad_none)
    val trailColor = MaterialTheme.colorScheme.primary.toArgb()

    PreferenceScaffold(
        label = stringResource(R.string.fg_test_pad_label),
        // Pinned (non collapsing) top bar: the pad must not move under the finger.
        isExpandedScreen = true,
        backArrowVisible = true,
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(
                    text = result ?: stringResource(R.string.fg_test_pad_hint),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(20.dp),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            GestureTestPadView(ctx).apply {
                                setTrailColor(trailColor)
                                onGesture = { kind ->
                                    result = if (kind == null) {
                                        noneText
                                    } else {
                                        val action = prefs.gestureHandler(kind).get().firstBlocking().getLabel(ctx)
                                        ctx.getString(R.string.fg_test_pad_result, ctx.getString(GestureLabels.labelRes(kind)), action)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
