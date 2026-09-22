package de.szalkowski.activitylauncher.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Keeps only the latest notification in memory; nothing is persisted or transmitted. */
object LastNotificationCache {
    data class Entry(val packageName: String, val title: String, val text: String, val timestamp: Long = System.currentTimeMillis())

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun update(packageName: String, title: String, text: String) {
        val entry = Entry(packageName, maskSensitive(title).trim(), maskSensitive(text).trim())
        if (entry.title.isBlank() && entry.text.isBlank()) return
        entries.removeAll { it.packageName == packageName && it.title == entry.title && it.text == entry.text }
        entries.addFirst(entry)
        while (entries.size > 50) entries.removeLast()
    }

    @Synchronized
    fun all(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun maskSensitive(text: String): String =
        text.replace(Regex("[0-9][0-9,]{5,}"), "****")
}

class SamNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            .ifBlank { extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty() }
        LastNotificationCache.update(sbn.packageName, title, text)
    }
}
