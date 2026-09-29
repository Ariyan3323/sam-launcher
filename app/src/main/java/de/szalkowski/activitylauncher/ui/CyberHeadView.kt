package de.szalkowski.activitylauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.szalkowski.activitylauncher.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive cute AI Orb character (inspired by Kimi with custom wings and dynamic mood):
 * - 3D glossy gradient body that floats and breathes
 * - Two cute fluttering wings on the sides
 * - Expressive pill eyes that look around, blink, and track touch
 * - Color shifting glow (cyan, electric blue, purple, magenta)
 * - Agitated/reactive state on touch/tap: shakes, wings flap furiously, eyes narrow into angry slant!
 */
class CyberHeadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val eyeRect = RectF()
    private val leftWingPath = Path()
    private val rightWingPath = Path()

    private val startedAt = System.nanoTime()
    private var isFrameScheduled = false

    // Gaze and rotation
    private var currentGazeX = 0f
    private var currentGazeY = 0f
    private var targetGazeX = 0f
    private var targetGazeY = 0f

    // Touch and mood tracking
    private var isTouching = false
    private var touchX = 0f
    private var touchY = 0f
    private var angerLevel = 0f // 1.0 = highly agitated / nervous, decays to 0.0
    private var lastFrameTime = System.currentTimeMillis()

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            postInvalidateOnAnimation()
            postDelayed(this, 16L) // 60 FPS
        }
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.cyber_avatar)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isFrameScheduled) {
            isFrameScheduled = true
            post(frameRunnable)
        }
    }

    override fun onDetachedFromWindow() {
        isFrameScheduled = false
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                touchX = event.x
                touchY = event.y
                angerLevel = 1.0f // Poke triggers angry / excited mood!
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
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

        val now = System.currentTimeMillis()
        val dt = ((now - lastFrameTime) / 1000f).coerceIn(0.001f, 0.1f)
        lastFrameTime = now

        // Decay anger
        if (!isTouching && angerLevel > 0f) {
            angerLevel = (angerLevel - dt * 0.75f).coerceAtLeast(0f)
        }

        val seconds = (System.nanoTime() - startedAt) / 1_000_000_000f

        // 1. Shifting Color Theme (Electric Cyan -> Blue -> Purple -> Pink, with fiery red/amber on anger)
        val normalHueShift = (seconds * 0.4f) % (2f * PI.toFloat())
        val baseR = ((sin(normalHueShift) * 0.5f + 0.5f) * 60).toInt() + 20
        val baseG = ((sin(normalHueShift + 2f) * 0.5f + 0.5f) * 120).toInt() + 100
        val baseB = ((sin(normalHueShift + 4f) * 0.5f + 0.5f) * 70).toInt() + 185

        // Blend with angry fiery magenta/amber when agitated
        val angerR = 255
        val angerG = ((sin(seconds * 15f) * 0.5f + 0.5f) * 80).toInt() + 40
        val angerB = 90

        val r = (baseR * (1f - angerLevel) + angerR * angerLevel).toInt().coerceIn(0, 255)
        val g = (baseG * (1f - angerLevel) + angerG * angerLevel).toInt().coerceIn(0, 255)
        val b = (baseB * (1f - angerLevel) + angerB * angerLevel).toInt().coerceIn(0, 255)

        val coreColor = Color.rgb(r, g, b)
        val darkBodyColor = Color.rgb((r * 0.35f).toInt(), (g * 0.35f).toInt(), (b * 0.35f).toInt())
        val lightBodyColor = Color.rgb(
            (r + (255 - r) * 0.65f).toInt(),
            (g + (255 - g) * 0.65f).toInt(),
            (b + (255 - b) * 0.65f).toInt()
        )

        // 2. Physics & Floating Motion
        val floatOffset = sin(seconds * 2.8f) * (h * 0.035f)
        val shakeOffset = if (angerLevel > 0.05f) {
            sin(seconds * 42f) * (w * 0.032f * angerLevel)
        } else 0f

        val centerX = (w * 0.5f) + shakeOffset
        val centerY = (h * 0.5f) + floatOffset
        val bodyRadius = w.coerceAtMost(h) * 0.28f

        // 3. Draw Fluttering Wings (behind the body)
        drawWings(canvas, centerX, centerY, bodyRadius, seconds, coreColor, angerLevel)

        // 4. Outer Glowing Aura
        val pulse = sin(seconds * 3.5f) * 0.5f + 0.5f
        val glowRadius = bodyRadius * (1.35f + pulse * 0.15f + angerLevel * 0.25f)
        glowPaint.shader = RadialGradient(
            centerX, centerY, glowRadius,
            intArrayOf(Color.argb((90 + angerLevel * 80).toInt().coerceAtMost(255), r, g, b), Color.TRANSPARENT),
            floatArrayOf(0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // 5. Glossy 3D Body Sphere
        bodyPaint.shader = RadialGradient(
            centerX - bodyRadius * 0.3f,
            centerY - bodyRadius * 0.35f,
            bodyRadius * 1.35f,
            intArrayOf(lightBodyColor, coreColor, darkBodyColor),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, bodyRadius, bodyPaint)

        // Specular 3D Highlight at top-left
        highlightPaint.shader = RadialGradient(
            centerX - bodyRadius * 0.38f,
            centerY - bodyRadius * 0.42f,
            bodyRadius * 0.45f,
            intArrayOf(Color.argb(190, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX - bodyRadius * 0.38f, centerY - bodyRadius * 0.42f, bodyRadius * 0.45f, highlightPaint)

        // 6. Gaze & Eye Tracking
        if (isTouching) {
            val normX = ((touchX - centerX) / (w * 0.5f)).coerceIn(-1f, 1f)
            val normY = ((touchY - centerY) / (h * 0.5f)).coerceIn(-1f, 1f)
            targetGazeX = normX * (bodyRadius * 0.32f)
            targetGazeY = normY * (bodyRadius * 0.26f)
        } else {
            // Natural wandering looking around smoothly
            targetGazeX = sin(seconds * 0.8f) * (bodyRadius * 0.22f)
            targetGazeY = cos(seconds * 0.6f + 0.5f) * (bodyRadius * 0.14f)
        }

        currentGazeX += (targetGazeX - currentGazeX) * 0.15f
        currentGazeY += (targetGazeY - currentGazeY) * 0.15f

        // 7. Blinking calculation
        val blinkCycle = seconds % 4.2f
        val isBlink = blinkCycle in 3.8f..4.0f
        val blinkScaleY = if (isBlink) {
            val p = (blinkCycle - 3.8f) / 0.2f
            abs(sin(p * PI.toFloat())) * 0.1f
        } else 1.0f

        // 8. Draw Cute Pill-Shaped Expressive Eyes
        val eyeSpacing = bodyRadius * 0.44f
        val baseEyeWidth = bodyRadius * 0.20f
        val baseEyeHeight = bodyRadius * 0.38f * blinkScaleY.coerceAtLeast(0.08f)

        // Draw Left Eye
        drawEye(
            canvas = canvas,
            eyeCenterX = centerX + currentGazeX - eyeSpacing * 0.5f,
            eyeCenterY = centerY + currentGazeY - bodyRadius * 0.05f,
            eyeWidth = baseEyeWidth,
            eyeHeight = baseEyeHeight,
            tiltDegrees = if (angerLevel > 0.05f) 22f * angerLevel else 0f
        )

        // Draw Right Eye
        drawEye(
            canvas = canvas,
            eyeCenterX = centerX + currentGazeX + eyeSpacing * 0.5f,
            eyeCenterY = centerY + currentGazeY - bodyRadius * 0.05f,
            eyeWidth = baseEyeWidth,
            eyeHeight = baseEyeHeight,
            tiltDegrees = if (angerLevel > 0.05f) -22f * angerLevel else 0f
        )
    }

    private fun drawWings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        seconds: Float,
        glowColor: Int,
        anger: Float
    ) {
        // Wing flap speed accelerates when agitated!
        val flapSpeed = 4.5f + anger * 16f
        val flapAngle = sin(seconds * flapSpeed) * (20f + anger * 18f)

        wingPaint.color = Color.argb(160, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        wingStrokePaint.color = Color.argb(220, 255, 255, 255)

        val wingWidth = radius * 0.65f
        val wingHeight = radius * 0.38f

        // Left Wing
        canvas.save()
        val leftAttachX = cx - radius * 0.82f
        val leftAttachY = cy - radius * 0.05f
        canvas.rotate(-15f + flapAngle, leftAttachX, leftAttachY)

        leftWingPath.reset()
        leftWingPath.moveTo(leftAttachX, leftAttachY)
        leftWingPath.cubicTo(
            leftAttachX - wingWidth * 0.6f, leftAttachY - wingHeight * 1.4f,
            leftAttachX - wingWidth * 1.1f, leftAttachY - wingHeight * 0.2f,
            leftAttachX - wingWidth, leftAttachY + wingHeight * 0.4f
        )
        leftWingPath.cubicTo(
            leftAttachX - wingWidth * 0.7f, leftAttachY + wingHeight * 0.9f,
            leftAttachX - wingWidth * 0.2f, leftAttachY + wingHeight * 0.6f,
            leftAttachX, leftAttachY
        )
        leftWingPath.close()

        canvas.drawPath(leftWingPath, wingPaint)
        canvas.drawPath(leftWingPath, wingStrokePaint)
        canvas.restore()

        // Right Wing
        canvas.save()
        val rightAttachX = cx + radius * 0.82f
        val rightAttachY = cy - radius * 0.05f
        canvas.rotate(15f - flapAngle, rightAttachX, rightAttachY)

        rightWingPath.reset()
        rightWingPath.moveTo(rightAttachX, rightAttachY)
        rightWingPath.cubicTo(
            rightAttachX + wingWidth * 0.6f, rightAttachY - wingHeight * 1.4f,
            rightAttachX + wingWidth * 1.1f, rightAttachY - wingHeight * 0.2f,
            rightAttachX + wingWidth, rightAttachY + wingHeight * 0.4f
        )
        rightWingPath.cubicTo(
            rightAttachX + wingWidth * 0.7f, rightAttachY + wingHeight * 0.9f,
            rightAttachX + wingWidth * 0.2f, rightAttachY + wingHeight * 0.6f,
            rightAttachX, rightAttachY
        )
        rightWingPath.close()

        canvas.drawPath(rightWingPath, wingPaint)
        canvas.drawPath(rightWingPath, wingStrokePaint)
        canvas.restore()
    }

    private fun drawEye(
        canvas: Canvas,
        eyeCenterX: Float,
        eyeCenterY: Float,
        eyeWidth: Float,
        eyeHeight: Float,
        tiltDegrees: Float
    ) {
        canvas.save()
        if (tiltDegrees != 0f) {
            canvas.rotate(tiltDegrees, eyeCenterX, eyeCenterY)
        }

        eyeRect.set(
            eyeCenterX - eyeWidth * 0.5f,
            eyeCenterY - eyeHeight * 0.5f,
            eyeCenterX + eyeWidth * 0.5f,
            eyeCenterY + eyeHeight * 0.5f
        )
        val cornerRadius = eyeWidth * 0.48f
        canvas.drawRoundRect(eyeRect, cornerRadius, cornerRadius, eyePaint)

        // Cute glossy dot inside eye
        if (eyeHeight > eyeWidth * 0.8f) {
            eyePaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawCircle(
                eyeCenterX - eyeWidth * 0.12f,
                eyeCenterY - eyeHeight * 0.22f,
                eyeWidth * 0.22f,
                eyePaint
            )
            eyePaint.color = Color.WHITE
        }

        canvas.restore()
    }
}
