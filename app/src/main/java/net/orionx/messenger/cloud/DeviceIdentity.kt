package net.orionx.messenger.cloud

import android.content.Context
import java.util.UUID

/** Stable per-installation identifier. It is not Android ID and contains no hardware identifier. */
object DeviceIdentity {
    private const val PREFS = "orionx_device_identity"
    private const val KEY = "installation_id"

    fun id(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }

    /** Выдаёт новый идентификатор установки, забывая старый. Нужен, когда сервер
     *  считает эту установку отозванной (так делает выход из аккаунта): успешный
     *  вход должен привязать устройство заново, а не выкидывать пользователя. */
    fun reset(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY, created).apply()
        return created
    }
}
