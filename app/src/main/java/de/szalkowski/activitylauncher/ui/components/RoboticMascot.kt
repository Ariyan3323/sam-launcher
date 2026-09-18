package de.szalkowski.activitylauncher.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import de.szalkowski.activitylauncher.agent.data.CaregiverMessageEntity

/** XML/View equivalent of the requested Compose RoboticMascot component. */
class RoboticMascotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var isOnline: Boolean = true
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val floatAnimator = ValueAnimator.ofFloat(-4f, 4f).apply {
        duration = 1200L
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); floatAnimator.start() }
    override fun onDetachedFromWindow() { floatAnimator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat().coerceAtLeast(1f)
        val center = size / 2f
        val neon = if (isOnline) Color.rgb(0, 255, 102) else Color.rgb(255, 51, 68)
        canvas.save()
        canvas.translate(0f, phase)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(10, 14, 23)
        canvas.drawCircle(center, center, size * .42f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .025f
        paint.color = neon
        canvas.drawCircle(center, center, size * .42f, paint)
        paint.style = Paint.Style.FILL
        paint.color = neon
        val eyeY = center - size * .06f
        val eyeRadius = size * .055f
        canvas.drawCircle(center - size * .12f, eyeY, eyeRadius, paint)
        canvas.drawCircle(center + size * .12f, eyeY, eyeRadius, paint)
        paint.strokeWidth = size * .025f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawRoundRect(RectF(center - size * .11f, center + size * .11f, center + size * .11f, center + size * .14f), size * .02f, size * .02f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .018f
        canvas.drawLine(center, center - size * .42f, center, center - size * .52f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(center, center - size * .54f, size * .025f, paint)
        canvas.restore()
    }
}
