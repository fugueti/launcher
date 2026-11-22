/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.icons.mono

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import app.lawnchair.icons.shouldTransparentBGIcons
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawableDelegate
import com.android.launcher3.icons.FastBitmapDrawableDelegate.DelegateFactory
import com.android.launcher3.icons.GraphicsUtils.getColorMultipliedFilter
import com.android.launcher3.icons.GraphicsUtils.resizeToContentSize
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.R

/** Drawing delegate handle monochrome themed app icons */
class ThemedIconDelegate(
    constantState: ThemedIconInfo,
    val bitmapInfo: BitmapInfo,
    val paint: Paint,
) : FastBitmapDrawableDelegate {

    private val colorFg = constantState.colorFg

    // The foreground/monochrome icon for the app
    private val monoIcon = constantState.mono
    private val monoPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = getColorMultipliedFilter(colorFg, paint.colorFilter)
        }

    private val shapeBounds = Rect(0, 0, bitmapInfo.icon.width, bitmapInfo.icon.height)

    init {
        paint.color = constantState.colorBg
    }

    override fun drawContent(
        info: BitmapInfo,
        iconShape: IconShape,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {
        canvas.drawBitmap(iconShape.shadowLayer, null, bounds, paint)

        canvas.resizeToContentSize(bounds, iconShape.pathSize.toFloat()) {
            clipPath(iconShape.path)
            drawPaint(paint)
            drawBitmap(monoIcon, null, shapeBounds, monoPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        monoPaint.alpha = alpha
    }

    override fun updateFilter(filter: ColorFilter?) {
        monoPaint.colorFilter = getColorMultipliedFilter(colorFg, filter)
    }

    override fun isThemed() = true

    override fun getIconColor(info: BitmapInfo) = colorFg

    companion object {
        const val TAG: String = "ThemedIconDrawable"

        /** Lawnchair: loader for the themed icon [background, foreground] colors. */
        @JvmStatic
        var COLORS_LOADER: (Context) -> IntArray = { context ->
            intArrayOf(
                context.resources.getColor(R.color.themed_icon_background_color),
                context.resources.getColor(R.color.themed_icon_color),
            )
        }

        /** Lawnchair: [background, foreground] as an array, as Lawnchair code expects. */
        @JvmStatic
        fun getColorArray(context: Context): IntArray =
            getColors(context).let { intArrayOf(it.iconBackgroundColor, it.iconForegroundColor) }

        /** Get an int array representing background and foreground colors for themed icons */
        @JvmStatic
        fun getColors(context: Context): ColorList {
            val res = context.resources
            // Lawnchair: background/foreground come from the host app's theme (which may follow
            // an in-app preference rather than the system night mode). Transparent backgrounds
            // only drop the background color.
            val colors = COLORS_LOADER(context)
            return ColorList(
                iconBackgroundColor =
                    if (context.shouldTransparentBGIcons()) Color.TRANSPARENT else colors[0],
                iconForegroundColor = colors[1],
                iconAdaptiveBackgroundColor =
                    res.getColor(R.color.themed_icon_adaptive_background_color),
                badgeBackgroundColor = res.getColor(R.color.themed_badge_icon_background_color),
                badgeForegroundColor = res.getColor(R.color.themed_badge_icon_color),
            )
        }
    }
}

class ThemedIconInfo(val mono: Bitmap, val colorBg: Int, val colorFg: Int) : DelegateFactory {

    override fun newDelegate(
        bitmapInfo: BitmapInfo,
        iconShape: IconShape,
        paint: Paint,
        host: FastBitmapDrawable,
    ) = ThemedIconDelegate(this, bitmapInfo, paint)
}

data class ColorList(
    val iconBackgroundColor: Int,
    val iconForegroundColor: Int,
    val iconAdaptiveBackgroundColor: Int,
    val badgeBackgroundColor: Int,
    val badgeForegroundColor: Int,
)
