package et.ethiolive.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import et.ethiolive.app.EthioLiveApp
import et.ethiolive.app.MainActivity
import et.ethiolive.app.R
import kotlinx.coroutines.launch

/** Receives FCM pushes queued by the send-push edge function and registers this device's token with the server. */
class EthioMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as EthioLiveApp
        app.container.backend.scope.launch { app.container.registerPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // With the app in the foreground FCM does not draw "notification" messages, so we do it here.
        val n = message.notification ?: return
        val type = message.data["type"] ?: "activity"
        show(this, type, n.title ?: getString(R.string.app_name), n.body.orEmpty(), message.data["notification_id"])
    }

    companion object {
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("messages", context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel("activity", context.getString(R.string.channel_activity), NotificationManager.IMPORTANCE_DEFAULT))
        }

        fun show(context: Context, type: String, title: String, body: String, id: String?) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannels(context)
            val intent = Intent(context, MainActivity::class.java)
                .putExtra("open", if (type == "message") "messages" else "notifications")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(context, type.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, if (type == "message") "messages" else "activity")
                .setSmallIcon(R.drawable.ic_stat_live)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(context).notify(id?.toIntOrNull() ?: System.currentTimeMillis().toInt(), notification)
        }
    }
}
