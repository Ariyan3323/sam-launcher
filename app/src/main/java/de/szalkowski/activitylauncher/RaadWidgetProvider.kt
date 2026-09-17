package de.szalkowski.activitylauncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.net.HttpURLConnection
import java.net.URL

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
            Thread {
                val headline = runCatching {
                    val connection = (URL("https://news.google.com/rss?hl=fa&gl=IR&ceid=IR:fa").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4_000
                        readTimeout = 6_000
                    }
                    val xml = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    Regex("<item>[\\s\\S]*?<title>(.*?)</title>").find(xml)?.groupValues?.get(1)
                        ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.trim()
                }.getOrNull()
                val views = RemoteViews(context.packageName, R.layout.widget_raad)
                views.setOnClickPendingIntent(R.id.widgetRoot, openAssistant)
                views.setTextViewText(R.id.widgetNews, headline?.let { "خبر تازه: $it" } ?: context.getString(R.string.assistant_widget_news_offline))
                manager.updateAppWidget(id, views)
            }.start()
        }
    }
}
