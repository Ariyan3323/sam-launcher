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
    enum class State { READY, LISTENING, THINKING, SPEAKING, ERROR }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var palette = 0
    private var state = State.READY

    fun setState(next: State) {
        state = next
        invalidate()
    }

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
        val colors = when (state) {
            State.READY -> arrayOf(
                intArrayOf(Color.rgb(93, 88, 240), Color.rgb(18, 190, 210)),
                intArrayOf(Color.rgb(220, 77, 145), Color.rgb(125, 74, 235)),
                intArrayOf(Color.rgb(242, 151, 55), Color.rgb(231, 72, 86))
            )[palette]
            State.LISTENING -> intArrayOf(Color.rgb(0, 196, 180), Color.rgb(42, 238, 210))
            State.THINKING -> intArrayOf(Color.rgb(88, 75, 220), Color.rgb(160, 92, 244))
            State.SPEAKING -> intArrayOf(Color.rgb(24, 173, 232), Color.rgb(111, 243, 255))
            State.ERROR -> intArrayOf(Color.rgb(218, 62, 91), Color.rgb(247, 126, 93))
        }
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        val radius = size * (.43f + if (state == State.LISTENING || state == State.SPEAKING) phase * .025f else 0f)
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .018f
        paint.color = Color.argb(175, 190, 246, 255)
        canvas.drawCircle(centerX, centerY, radius * .78f, paint)
        canvas.save()
        canvas.rotate(-24f + phase * 18f, centerX, centerY)
        canvas.drawOval(
            centerX - radius * .86f, centerY - radius * .27f,
            centerX + radius * .86f, centerY + radius * .27f, paint
        )
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(centerX + radius * .64f, centerY - radius * .18f, size * .035f, paint)

        paint.color = Color.WHITE
        val eyeY = centerY - size * .07f
        val blink = if (state == State.SPEAKING || state == State.LISTENING) {
            kotlin.math.abs(kotlin.math.sin(phase * Math.PI * 2.0)).toFloat()
        } else 0f
        val eyeRadius = size * .055f
        val eyeHeight = if (blink > .94f) size * .012f else eyeRadius
        canvas.drawOval(centerX - size * .14f - eyeRadius, eyeY - eyeHeight, centerX - size * .14f + eyeRadius, eyeY + eyeHeight, paint)
        canvas.drawOval(centerX + size * .14f - eyeRadius, eyeY - eyeHeight, centerX + size * .14f + eyeRadius, eyeY + eyeHeight, paint)
        paint.color = Color.rgb(35, 35, 70)
        if (eyeHeight > eyeRadius * .5f) {
            canvas.drawCircle(centerX - size * .14f, eyeY, eyeRadius * .35f, paint)
            canvas.drawCircle(centerX + size * .14f, eyeY, eyeRadius * .35f, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * .045f
        paint.strokeCap = Paint.Cap.ROUND
        if (state == State.SPEAKING) {
            val mouth = size * (.10f + phase * .055f)
            canvas.drawOval(centerX - mouth, centerY + size * .035f, centerX + mouth, centerY + size * .035f + size * .055f, paint)
        } else {
            canvas.drawArc(centerX - size * .16f, centerY - size * .01f, centerX + size * .16f, centerY + size * .25f, 25f, 130f, false, paint)
        }
        paint.style = Paint.Style.FILL
    }
}
