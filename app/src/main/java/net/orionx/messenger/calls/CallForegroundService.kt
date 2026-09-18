package net.orionx.messenger.calls

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
import net.orionx.messenger.MainActivity
import net.orionx.messenger.R

class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val video = intent?.getBooleanExtra(EXTRA_VIDEO, false) == true
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "OrionX" }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            68001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (video) "Видеозвонок OrionX" else "Аудиозвонок OrionX")
            .setContentText("Разговор с $peerName")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (video) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "orionx_calls"
        private const val NOTIFICATION_ID = 68001
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_PEER_NAME = "peer_name"

        fun start(context: Context, video: Boolean, peerName: String) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_VIDEO, video)
                .putExtra(EXTRA_PEER_NAME, peerName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Звонки OrionX",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Активные аудио- и видеозвонки OrionX"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
