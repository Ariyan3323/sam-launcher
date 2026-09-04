package de.szalkowski.activitylauncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RaadWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { id ->
            val openAssistant = PendingIntent.getActivity(
                context, id, Intent(context, AssistantActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_raad).apply {
                setOnClickPendingIntent(R.id.widgetRoot, openAssistant)
            })
        }
    }
}
