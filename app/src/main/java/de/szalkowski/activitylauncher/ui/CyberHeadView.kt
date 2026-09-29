package de.szalkowski.activitylauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageView
import de.szalkowski.activitylauncher.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Highly interactive, alive cybernetic avatar head:
 * - 3D parallax head rotation (pitch/yaw) with smooth looking-around gestures
 * - Touch-reactive tracking (head and gaze turn toward user touch/drag)
 * - Shifting cyber neon light aura and color breathing
 * - Detailed animated glowing ocular cores with blinking, pupil dilation, and scanning rings
 */
class CyberHeadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatImageView(context, attrs) {

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyeCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val eyeBounds = RectF()
    private val auraBounds = RectF()

    private var startedAt = System.nanoTime()
    private var framePosted = false

    // Target and current rotation for smooth interpolation
    private var currentRotX = 0f
    private var currentRotY = 0f
    private var targetRotX = 0f
    private var targetRotY = 0f

    // Touch tracking
    private var isTouched = false
    private var touchX = 0f
    private var touchY = 0f
    private var touchReleaseTime = 0L

    private val frame = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            postInvalidateOnAnimation()
            postDelayed(this, 16L) // ~60 FPS
        }
    }

    init {
        setImageResource(R.drawable.cyber_head_reference)
        scaleType = ImageView.ScaleType.CENTER_CROP
        adjustViewBounds = true
        contentDescription = context.getString(R.string.cyber_avatar)
        isClickable = true
        isFocusable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Deepen 3D perspective
        val density = resources.displayMetrics.density
        cameraDistance = density * 8000f

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isTouched = true
                touchX = event.x
                touchY = event.y
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouched = false
                touchReleaseTime = System.currentTimeMillis()
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f

        // 1. Organic Idle or Touch Looking Behavior
        if (isTouched) {
            val normX = ((touchX / w) - 0.5f).coerceIn(-0.5f, 0.5f)
            val normY = ((touchY / h) - 0.5f).coerceIn(-0.5f, 0.5f)
            targetRotY = normX * 24f // Head turns left/right toward finger
            targetRotX = -normY * 18f // Head tilts up/down
        } else {
            val sinceRelease = (System.currentTimeMillis() - touchReleaseTime) / 1000f
            // Periodically glance around smoothly
            val wanderX = sin(seconds * 0.45f) * 10f + sin(seconds * 1.1f) * 4f
            val wanderY = cos(seconds * 0.35f + 0.5f) * 6f + sin(seconds * 0.8f) * 2f
            val breathingPitch = sin(seconds * PI.toFloat() / 2.6f) * 1.8f

            targetRotY = wanderX
            targetRotX = wanderY + breathingPitch
        }

        // Smooth easing toward target
        val lerpFactor = if (isTouched) 0.18f else 0.08f
        currentRotX += (targetRotX - currentRotX) * lerpFactor
        currentRotY += (targetRotY - currentRotY) * lerpFactor

        rotationX = currentRotX
        rotationY = currentRotY
        translationY = sin(seconds * PI.toFloat() / 2.6f) * (h * 0.02f)

        // 2. Dynamic Shifting Cyber Color Aura (Cyan -> Blue -> Violet -> Neon Green)
        val colorPhase = (seconds * 0.6f) % (2f * PI.toFloat())
        val red = ((sin(colorPhase) * 0.5f + 0.5f) * 80).toInt() + 20
        val green = ((sin(colorPhase + 2f) * 0.5f + 0.5f) * 160).toInt() + 70
        val blue = ((sin(colorPhase + 4f) * 0.5f + 0.5f) * 130).toInt() + 125
        val cyberColor = Color.rgb(red, green, blue)

        // Draw pulsating cyber halo aura behind the image
        val pulse = (sin(seconds * 3.2f) * 0.5f + 0.5f)
        val auraRadius = (w.coerceAtLeast(h) * 0.55f) + pulse * 14f
        auraPaint.shader = RadialGradient(
            w * 0.5f, h * 0.5f, auraRadius,
            intArrayOf(Color.argb((60 + pulse * 45).toInt(), red, green, blue), Color.TRANSPARENT),
            floatArrayOf(0.4f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.5f, h * 0.5f, auraRadius, auraPaint)

        // 3. Draw Base Face Image
        super.onDraw(canvas)

        // 4. Draw Animated 3D Cyber Eyes & Visor
        // Perspective shift based on head rotation
        val gazeOffsetX = (currentRotY / 24f) * (w * 0.025f)
        val gazeOffsetY = (-currentRotX / 18f) * (h * 0.02f)

        // Blinking calculation with occasional double-blink
        val blinkCycle = seconds % 5.5f
        val isBlinking = blinkCycle in 4.9f..5.1f || blinkCycle in 5.25f..5.38f
        val eyeOpen = if (isBlinking) {
            val progress = ((blinkCycle - 4.9f) / 0.2f).coerceIn(0f, 1f)
            abs(sin(progress * PI.toFloat() * 2f)) * 0.15f
        } else {
            1f
        }

        // Eye positions matched to front-facing reference portrait
        val leftEyeX = w * 0.38f + gazeOffsetX
        val rightEyeX = w * 0.65f + gazeOffsetX
        val eyeY = h * 0.46f + gazeOffsetY
        val eyeRadiusX = w * 0.048f
        val eyeRadiusY = (h * 0.022f) * eyeOpen.coerceAtLeast(0.08f)

        drawCyberEye(canvas, leftEyeX, eyeY, eyeRadiusX, eyeRadiusY, cyberColor, pulse, seconds)
        drawCyberEye(canvas, rightEyeX, eyeY, eyeRadiusX, eyeRadiusY, cyberColor, pulse, seconds)

        // 5. Futuristic Rotating Holo-Rings around head
        val ringAngle = seconds * 45f
        ringPaint.strokeWidth = 2.5f
        ringPaint.color = Color.argb((80 + pulse * 70).toInt(), red, green, blue)
        auraBounds.set(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.92f)
        canvas.drawArc(auraBounds, ringAngle, 70f, false, ringPaint)
        canvas.drawArc(auraBounds, ringAngle + 180f, 70f, false, ringPaint)

        ringPaint.strokeWidth = 1.5f
        ringPaint.color = Color.argb(100, 50, 230, 255)
        canvas.drawArc(auraBounds, -ringAngle * 0.7f, 45f, false, ringPaint)
        canvas.drawArc(auraBounds, -ringAngle * 0.7f + 180f, 45f, false, ringPaint)
    }

    private fun drawCyberEye(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        glowColor: Int,
        pulse: Float,
        seconds: Float
    ) {
        eyeBounds.set(cx - rx, cy - ry, cx + rx, cy + ry)

        // Outer glow
        eyeGlowPaint.color = Color.argb((110 + pulse * 60).toInt(), Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        canvas.drawOval(eyeBounds, eyeGlowPaint)

        // Core iris
        val coreRx = rx * 0.75f
        val coreRy = ry * 0.85f
        eyeCorePaint.color = Color.argb(220, 180, 245, 255)
        canvas.drawOval(cx - coreRx, cy - coreRy, cx + coreRx, cy + coreRy, eyeCorePaint)

        // High-tech pupil / cyber-slit
        val pupilRx = rx * 0.32f
        val pupilRy = ry * 0.65f
        pupilPaint.color = Color.rgb(10, 25, 45)
        canvas.drawOval(cx - pupilRx, cy - pupilRy, cx + pupilRx, cy + pupilRy, pupilPaint)

        // Glowing center dot (laser iris)
        pupilPaint.color = glowColor
        canvas.drawCircle(cx, cy, (rx * 0.15f) * (0.8f + pulse * 0.4f), pupilPaint)

        // Horizontal scanner line sweep through the eye
        val sweepY = cy + sin(seconds * 4f) * ry * 0.8f
        scanLinePaint.color = Color.argb(160, 255, 255, 255)
        scanLinePaint.strokeWidth = 1.2f
        canvas.drawLine(cx - rx * 0.8f, sweepY, cx + rx * 0.8f, sweepY, scanLinePaint)
    }
}
