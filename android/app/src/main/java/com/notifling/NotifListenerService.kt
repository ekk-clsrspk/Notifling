package com.notifling

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Forwards posted notifications to the paired PC over UDP. */
class NotifListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Skip ongoing (music, navigation), our own package, and clearable-less system noise.
        if (sbn.isOngoing) return
        if (sbn.packageName == packageName) return
        val notif: Notification = sbn.notification
        if (notif.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val store = KeyStore(this)
        val key = store.getKey()
        if (key.isEmpty()) {
            store.setLastStatus("no key set")
            return
        }
        scope.launch {
            val status = UdpSender.sendNotification(key, sbn.packageName, title, text)
            store.setLastStatus(status)
        }
    }
}
