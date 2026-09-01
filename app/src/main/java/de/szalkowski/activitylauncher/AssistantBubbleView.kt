package de.szalkowski.activitylauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/** A small, always-on-screen assistant avatar that reacts while waiting for commands. */
class AssistantBubbleView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var palette = 0

    fun nextAppearance() {
        palette = (palette + 1) % 3
        invalidate()
    }
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f + (phase - .5f) * size * .06f
        val colors = arrayOf(
            intArrayOf(Color.rgb(93, 88, 240), Color.rgb(18, 190, 210)),
            intArrayOf(Color.rgb(220, 77, 145), Color.rgb(125, 74, 235)),
            intArrayOf(Color.rgb(242, 151, 55), Color.rgb(231, 72, 86))
        )[palette]
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        canvas.drawCircle(centerX, centerY, size * .43f, paint)
        paint.shader = null

        paint.color = Color.WHITE
        val eyeY = centerY - size * .07f
        val eyeRadius = size * (.055f + phase * .012f)
        canvas.drawCircle(centerX - size * .14f, eyeY, eyeRadius, paint)
        canvas.drawCircle(centerX + size * .14f, eyeY, eyeRadius, paint)
        paint.color = Color.rgb(35, 35, 70)
        canvas.drawCircle(centerX - size * .14f, eyeY, eyeRadius * .35f, paint)
        canvas.drawCircle(centerX + size * .14f, eyeY, eyeRadius * .35f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .045f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(
            centerX - size * .16f, centerY - size * .01f,
            centerX + size * .16f, centerY + size * .25f,
            25f, 130f, false, paint
        )
        paint.style = Paint.Style.FILL
    }
}
