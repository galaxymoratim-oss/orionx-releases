package net.orionx.messenger.push

import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.orionx.messenger.BuildConfig
import net.orionx.messenger.cloud.CloudSessionStore
import net.orionx.messenger.cloud.DeviceIdentity
import net.orionx.messenger.cloud.SupabaseApi

object PushTokenSync {
    private const val PREFS = "orionx_push"
    private const val TOKEN = "pending_token"

    fun savePending(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN, token).apply()
    }

    suspend fun syncCurrent(context: Context, api: SupabaseApi) = withContext(Dispatchers.IO) {
        val session = CloudSessionStore(context).load() ?: return@withContext
        syncSession(context, api, session)
    }

    suspend fun syncSession(context: Context, api: SupabaseApi, session: net.orionx.messenger.cloud.CloudSession) = withContext(Dispatchers.IO) {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TOKEN, null)
        val pushToken = stored ?: FirebaseMessaging.getInstance().token.await()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        var registered = api.registerPushToken(
            token = session.accessToken,
            userId = session.userId,
            installationId = DeviceIdentity.id(context),
            pushToken = pushToken,
            deviceName = deviceName,
            appVersion = BuildConfig.VERSION_NAME,
        )
        if (!registered) {
            // Установка отозвана (прошлый выход из аккаунта) — перевыпускаем идентификатор.
            registered = api.registerPushToken(
                token = session.accessToken,
                userId = session.userId,
                installationId = DeviceIdentity.reset(context),
                pushToken = pushToken,
                deviceName = deviceName,
                appVersion = BuildConfig.VERSION_NAME,
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(TOKEN).apply()
        registered
    }
}
