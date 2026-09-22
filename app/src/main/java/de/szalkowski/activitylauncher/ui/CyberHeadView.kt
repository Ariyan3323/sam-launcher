package de.szalkowski.activitylauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.ImageView
import de.szalkowski.activitylauncher.R
import kotlin.math.PI
import kotlin.math.sin

/** Dashboard-only cybernetic head: animated parallax, gaze drift, eye glow and blink. */
class CyberHeadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyeRect = RectF()
    private var startedAt = System.nanoTime()
    private var framePosted = false
    private val frame = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            postInvalidateOnAnimation()
            postDelayed(this, 32L)
        }
    }

    init {
        setImageResource(R.drawable.cyber_head_reference)
        scaleType = ImageView.ScaleType.CENTER_CROP
        adjustViewBounds = true
        contentDescription = context.getString(R.string.cyber_avatar)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!framePosted) {
            startedAt = System.nanoTime()
            framePosted = true
            post(frame)
        }
    }

    override fun onDetachedFromWindow() {
        framePosted = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f
        val breathing = sin(seconds * PI.toFloat() / 2.8f)
        val gazeX = sin(seconds * 0.75f) * width * 0.012f
        val gazeY = sin(seconds * 0.53f + 0.8f) * height * 0.009f
        val blinkWave = sin(seconds * PI.toFloat() / 5.7f)
        val blink = if (blinkWave > 0.985f) 0.08f else 1f

        rotationY = breathing * 2.8f
        rotationX = -breathing * 1.7f
        translationY = breathing * 1.8f

        super.onDraw(canvas)

        // Coordinates follow the eyes in the supplied portrait crop.
        val leftX = width * 0.405f + gazeX
        val rightX = width * 0.645f + gazeX
        val eyeY = height * 0.335f + gazeY
        val eyeWidth = width * 0.072f
        val eyeHeight = height * 0.035f * blink
        drawEye(canvas, leftX, eyeY, eyeWidth, eyeHeight)
        drawEye(canvas, rightX, eyeY, eyeWidth, eyeHeight)
    }

    private fun drawEye(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        eyeRect.set(x - width, y - height, x + width, y + height)
        glowPaint.color = Color.argb(100, 70, 235, 255)
        glowPaint.setShadowLayer(22f, 0f, 0f, Color.CYAN)
        canvas.drawOval(eyeRect, glowPaint)
        glowPaint.clearShadowLayer()

        eyePaint.color = Color.WHITE
        eyePaint.setShadowLayer(12f, 0f, 0f, Color.CYAN)
        canvas.drawOval(eyeRect, eyePaint)
        eyePaint.clearShadowLayer()
    }
}
