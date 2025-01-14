/*

   Copyright 2015 Andreas Würl

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package org.blitzortung.android.app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.blitzortung.android.app.Main
import org.blitzortung.android.app.R
import org.blitzortung.android.data.provider.result.ResultEvent
import org.blitzortung.android.map.overlay.StrikeListOverlay
import org.blitzortung.android.protocol.Event
import org.blitzortung.android.util.TabletAwareView

class HistogramView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : TabletAwareView(context, attrs, defStyle) {

    private val backgroundPaint: Paint
    private val foregroundPaint: Paint
    private val textPaint: Paint
    private val defaultForegroundColor: Int
    private val backgroundRect: RectF
    private var strikesOverlay: StrikeListOverlay? = null
    private var histogram: IntArray? = null

    val dataConsumer = { event: Event ->
        if (event is ResultEvent) {
            updateHistogram(event)
        }
    }

    init {
        foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backgroundPaint.color = context.resources.getColor(R.color.translucent_background)

        defaultForegroundColor = context.resources.getColor(R.color.text_foreground)
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = defaultForegroundColor
            textSize = this@HistogramView.textSize
            textAlign = Paint.Align.RIGHT
        }

        backgroundRect = RectF()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val getSize = fun(spec: Int) = MeasureSpec.getSize(spec)

        val parentWidth = getSize(widthMeasureSpec) * sizeFactor
        val parentHeight = getSize(heightMeasureSpec) * sizeFactor

        super.onMeasure(MeasureSpec.makeMeasureSpec(parentWidth.toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(parentHeight.toInt(), MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        val strikesOverlay = strikesOverlay
        val histogram = histogram
        if (strikesOverlay != null && histogram != null && histogram.size > 0) {
            val colorHandler = strikesOverlay.colorHandler
            val minutesPerColor = strikesOverlay.parameters.intervalDuration / colorHandler.numberOfColors
            val minutesPerBin = 5
            val ratio = minutesPerColor / minutesPerBin
            if (ratio == 0) {
                return
            }

            backgroundRect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRect(backgroundRect, backgroundPaint)

            val maximumCount = histogram.max() ?: 0

            canvas.drawText("%.1f/%s _".format(
                    maximumCount.toFloat() / minutesPerBin, resources.getString(R.string.unit_minute)), width - 2 * padding, padding + textSize / 1.2f, textPaint)

            val ymax = if (maximumCount == 0) 1 else maximumCount

            val x0 = padding
            val xd = (width - 2 * padding) / (histogram.size - 1)

            val y0 = height - padding
            val yd = (height - 2 * padding - textSize) / ymax

            foregroundPaint.strokeWidth = 2f
            for (i in 0..histogram.size - 1 - 1) {
                foregroundPaint.color = colorHandler.getColor((histogram.size - 1 - i) / ratio)
                canvas.drawLine(x0 + xd * i, y0 - yd * histogram[i], x0 + xd * (i + 1), y0 - yd * histogram[i + 1], foregroundPaint)
            }

            foregroundPaint.strokeWidth = 1f
            foregroundPaint.color = defaultForegroundColor

            canvas.drawLine(padding, height - padding, width - padding, height - padding, foregroundPaint)
            canvas.drawLine(width - padding, padding, width - padding, height - padding, foregroundPaint)
        }
    }

    fun setStrikesOverlay(strikesOverlay: StrikeListOverlay) {
        this.strikesOverlay = strikesOverlay
    }

    private fun updateHistogram(dataEvent: ResultEvent) {
        if (dataEvent.failed) {
            visibility = INVISIBLE
            histogram = null
        } else {
            val histogram = dataEvent.histogram

            var viewShouldBeVisible = histogram != null && histogram.size > 0

            this.histogram = histogram

            if (!viewShouldBeVisible) {
                viewShouldBeVisible = createHistogram(dataEvent)
            }

            visibility = if (viewShouldBeVisible) VISIBLE else INVISIBLE

            if (viewShouldBeVisible) {
                invalidate()
            }
        }
    }

    private fun createHistogram(result: ResultEvent): Boolean {
        result.parameters.let { parameters ->
            if (result.totalStrikes == null) {
                return false
            }

            Log.v(Main.LOG_TAG, "HistogramView create histogram from ${result.totalStrikes.size} total strikes")
            val referenceTime = result.referenceTime

            val binInterval = 5
            val binCount = parameters.intervalDuration / binInterval
            val histogram = IntArray(binCount)

            result.totalStrikes.forEach { strike ->
                val binIndex = (binCount - 1) - ((referenceTime - strike.timestamp) / 1000 / 60 / binInterval).toInt()
                if (binIndex in 0 .. binCount - 1)
                    histogram[binIndex]++
            }
            this.histogram = histogram
            return true
        }
    }
}

data class Strike(val timestamp: Long)
