package et.ethiolive.app.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import et.ethiolive.app.MainActivity
import et.ethiolive.app.R

/**
 * Foreground service that keeps the camera and microphone running while the host is live and briefly leaves the
 * app (Android stops background capture otherwise). It holds no media itself; the engine lives in the app container.
 */
class BroadcastService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val audioOnly = intent?.getBooleanExtra(EXTRA_AUDIO_ONLY, false) ?: false
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_live)
            .setContentTitle(getString(R.string.broadcast_notification_title))
            .setContentText(getString(R.string.broadcast_notification_body))
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (audioOnly) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "broadcast"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_STOP = "et.ethiolive.app.STOP_BROADCAST"
        private const val EXTRA_AUDIO_ONLY = "audio_only"

        fun start(context: Context, audioOnly: Boolean) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, BroadcastService::class.java).putExtra(EXTRA_AUDIO_ONLY, audioOnly))
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, BroadcastService::class.java).setAction(ACTION_STOP)) }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_broadcast), NotificationManager.IMPORTANCE_LOW))
        }
    }
}
