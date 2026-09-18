package net.orionx.messenger.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.orionx.messenger.MainActivity
import net.orionx.messenger.R

/** Per-conversation mute preference. Muted conversations don't post message notifications. */
object OrionMutedChats {
    private const val PREFS = "orionx_muted_chats"

    fun isMuted(context: Context, conversationId: String?): Boolean {
        if (conversationId.isNullOrBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(conversationId, false)
    }

    fun setMuted(context: Context, conversationId: String, muted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(conversationId, muted).apply()
    }
}

object OrionNotifications {
    const val CHANNEL_MESSAGES = "orionx_messages"
    const val CHANNEL_INCOMING_CALLS = "orionx_incoming_calls"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_messages)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        val calls = NotificationChannel(
            CHANNEL_INCOMING_CALLS,
            "Входящие звонки OrionX",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о входящих аудио- и видеозвонках OrionX"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(calls)
    }

    fun permissionGranted(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 33 || ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun show(
        context: Context,
        title: String,
        body: String,
        conversationId: String? = null,
        notificationId: Int = (conversationId ?: body).hashCode(),
    ): Boolean {
        ensureChannels(context)
        if (!permissionGranted(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            conversationId?.let { putExtra("conversation_id", it) }
        }
        val pending = PendingIntent.getActivity(
            context,
            conversationId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // «звук и вибрация» из настроек: выключено — уведомление приходит беззвучно.
            .setSilent(!context.getSharedPreferences("orionx_redesign_settings", Context.MODE_PRIVATE)
                .getBoolean("notify_sound", true))
            .setContentIntent(pending)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }


    fun showIncomingCall(
        context: Context,
        title: String,
        callerName: String,
        conversationId: String?,
        callId: String?,
    ): Boolean {
        ensureChannels(context)
        if (!permissionGranted(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            conversationId?.let { putExtra("conversation_id", it) }
            callId?.let { putExtra("call_id", it) }
        }
        val requestCode = (callId ?: conversationId ?: callerName).hashCode()
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(requestCode, notification)
        return true
    }

    fun showTest(context: Context): Boolean = show(
        context = context,
        title = "OrionX",
        body = "Тестовое уведомление OrionX работает.",
        notificationId = 63001,
    )
}
