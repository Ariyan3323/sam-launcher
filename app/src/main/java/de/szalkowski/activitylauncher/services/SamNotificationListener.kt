package de.szalkowski.activitylauncher.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Keeps only the latest notification in memory; nothing is persisted or transmitted. */
object LastNotificationCache {
    data class Entry(val packageName: String, val title: String, val text: String)

    @Volatile
    private var latest: Entry? = null

    fun update(packageName: String, title: String, text: String) {
        latest = Entry(packageName, maskSensitive(title), maskSensitive(text))
    }

    fun get(): Entry? = latest

    fun clear() {
        latest = null
    }

    private fun maskSensitive(text: String): String =
        text.replace(Regex("[0-9][0-9,]{5,}"), "****")
}

class SamNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        LastNotificationCache.update(
            sbn.packageName,
            extras.getString(Notification.EXTRA_TITLE).orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        )
    }
}
