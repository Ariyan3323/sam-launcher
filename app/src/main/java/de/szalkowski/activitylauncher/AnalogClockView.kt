package de.szalkowski.activitylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** High-contrast analogue clock used by the Raad home screen. */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var hour = 0
    private var minute = 0
    private var second = 0
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 191, 255)
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    private val hourHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 165, 0)
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val minuteHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 165, 0)
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val secondHand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    private val clockTicker = object : Runnable {
        override fun run() {
            updateFromSystemClock()
            if (isAttachedToWindow) postDelayed(this, CLOCK_REFRESH_MS)
        }
    }

    fun setTime(hour: Int, minute: Int, second: Int) {
        this.hour = hour
        this.minute = minute
        this.second = second
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(clockTicker)
        clockTicker.run()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clockTicker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(cx, cy) - 12f).coerceAtLeast(0f)
        canvas.drawCircle(cx, cy, radius, face)
        for (value in 1..12) {
            val angle = Math.toRadians((value * 30 - 90).toDouble())
            canvas.drawText(
                value.toString(),
                cx + (radius - 28) * cos(angle).toFloat(),
                cy + (radius - 28) * sin(angle).toFloat() + 10,
                number
            )
        }
        drawHand(canvas, cx, cy, radius * .5f, (hour % 12) * 30 + minute * .5 - 90, hourHand)
        drawHand(canvas, cx, cy, radius * .7f, minute * 6 - 90, minuteHand)
        drawHand(canvas, cx, cy, radius * .85f, second * 6 - 90, secondHand)
        canvas.drawCircle(cx, cy, 8f, secondHand)
    }

    private fun updateFromSystemClock() {
        val now = java.util.Calendar.getInstance()
        setTime(
            now.get(java.util.Calendar.HOUR_OF_DAY),
            now.get(java.util.Calendar.MINUTE),
            now.get(java.util.Calendar.SECOND)
        )
    }

    private fun drawHand(canvas: Canvas, x: Float, y: Float, length: Float, degrees: Double, paint: Paint) {
        val angle = Math.toRadians(degrees)
        canvas.drawLine(x, y, x + length * cos(angle).toFloat(), y + length * sin(angle).toFloat(), paint)
    }

    private companion object {
        const val CLOCK_REFRESH_MS = 1_000L
    }
}
