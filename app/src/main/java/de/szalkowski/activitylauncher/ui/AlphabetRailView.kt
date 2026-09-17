package de.szalkowski.activitylauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class AlphabetRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var language: Language = Language.PERSIAN
    var onLetterSelected: ((String) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selectedIndex = -1
    private var downY = 0f

    enum class Language { PERSIAN, LATIN }

    private val letters: List<String>
        get() = if (language == Language.PERSIAN) {
            "ابتثجچحخدذرزژسشصضطظعغفقکگلمنوهی".map { it.toString() }
        } else ('A'..'Z').map { it.toString() }

    init {
        isClickable = true
        isFocusable = true
        elevation = 12f * resources.displayMetrics.density
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        linePaint.strokeWidth = resources.displayMetrics.density
        linePaint.style = Paint.Style.STROKE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val centerX = width / 2f
        linePaint.color = 0x669E7BFF
        canvas.drawLine(centerX, 16f * d, centerX, height - 16f * d, linePaint)
        val step = (height - 28f * d) / letters.size.coerceAtLeast(1)
        letters.forEachIndexed { index, letter ->
            val y = 22f * d + index * step
            val active = index == selectedIndex
            if (active) {
                linePaint.color = 0xFFFF5FA2.toInt()
                canvas.drawCircle(centerX, y - 4f * d, 13f * d, linePaint)
            }
            paint.textSize = (if (active) 15f else 11f) * d
            paint.color = if (active) 0xFFFF5FA2.toInt() else 0xCCFFFFFF.toInt()
            canvas.drawText(letter, centerX, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                selectAt(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selectAt(event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                selectAt(event.y)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun selectAt(y: Float) {
        val top = 14f * resources.displayMetrics.density
        val usable = (height - 28f * resources.displayMetrics.density).coerceAtLeast(1f)
        val index = ((y - top) / usable * letters.size).roundToInt().coerceIn(0, letters.lastIndex)
        if (index != selectedIndex) {
            selectedIndex = index
            onLetterSelected?.invoke(letters[index])
            invalidate()
        }
    }

    fun clearSelection() {
        selectedIndex = -1
        invalidate()
    }
}
