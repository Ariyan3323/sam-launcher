package de.szalkowski.activitylauncher.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingDecoration(private val spacingDp: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val spacing = (spacingDp * view.resources.displayMetrics.density).toInt()
        outRect.set(spacing, spacing, spacing, spacing)
    }
}
