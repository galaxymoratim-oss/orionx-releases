package net.orionx.messenger.cloud

import android.content.Context

class CloudSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("orionx_cloud_session", Context.MODE_PRIVATE)
    fun save(session: CloudSession) = prefs.edit().putString("access", session.accessToken).putString("refresh", session.refreshToken).putString("uid", session.userId).putString("email", session.email).apply()
    fun load(): CloudSession? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val uid = prefs.getString("uid", null) ?: return null
        return CloudSession(access, refresh, uid, prefs.getString("email", "").orEmpty())
    }
    fun isPersisted(): Boolean = prefs.contains("refresh")
    fun clear() = prefs.edit().clear().apply()
}
