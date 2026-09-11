package de.szalkowski.activitylauncher.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.cos
import kotlin.math.sin

/** Alphabetical app items arranged on an interactive moon-like orbital arc. */
class OrbitalLayoutManager(context: Context) : RecyclerView.LayoutManager() {
    private var rotation = 0f
    private val density = context.resources.displayMetrics.density
    private val radius = 160 * density

    init {
        isItemPrefetchEnabled = true
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams((132 * density).toInt(), (156 * density).toInt())

    override fun canScrollHorizontally(): Boolean = true
    override fun canScrollVertically(): Boolean = false

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        detachAndScrapAttachedViews(recycler)
        if (itemCount == 0) return
        val centerX = width / 2
        val centerY = height / 2
        val visible = minOf(itemCount, 11)
        val step = 360f / visible
        val start = Math.floor((rotation / step).toDouble()).toInt() - visible / 2
        for (slot in 0 until visible) {
            val position = Math.floorMod(start + slot, itemCount)
            val child = recycler.getViewForPosition(position)
            addView(child)
            measureChildWithMargins(child, 0, 0)
            val angle = (slot * step - rotation).toDouble() * Math.PI / 180.0
            val x = centerX + (cos(angle) * radius).toInt() - getDecoratedMeasuredWidth(child) / 2
            val y = centerY + (sin(angle) * radius * 0.38).toInt() - getDecoratedMeasuredHeight(child) / 2
            layoutDecoratedWithMargins(
                child,
                x,
                y,
                x + getDecoratedMeasuredWidth(child),
                y + getDecoratedMeasuredHeight(child)
            )
            applyDepth(child, cos(angle).toFloat())
        }
    }

    private fun applyDepth(child: View, depth: Float) {
        val scale = 0.70f + ((depth + 1f) / 2f) * 0.38f
        child.scaleX = scale
        child.scaleY = scale
        child.alpha = 0.48f + ((depth + 1f) / 2f) * 0.52f
        child.translationZ = (depth + 1f) * 12f
    }

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (itemCount == 0) return 0
        rotation = (rotation + dx * 0.45f) % 360f
        onLayoutChildren(recycler, state)
        return dx
    }

    override fun scrollToPosition(position: Int) {
        if (itemCount == 0) return
        rotation = position.coerceIn(0, itemCount - 1) * (360f / minOf(itemCount, 11))
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        scrollToPosition(position)
    }
}
