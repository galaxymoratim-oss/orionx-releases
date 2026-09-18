package net.orionx.messenger.cloud

import android.util.Base64
import org.json.JSONObject

/** Returns true when the access token is expired or will expire soon. */
fun CloudSession.accessTokenNeedsRefresh(withinSeconds: Long = 120): Boolean {
    return runCatching {
        val parts = accessToken.split('.')
        if (parts.size < 2) return@runCatching true
        val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val exp = JSONObject(String(decoded, Charsets.UTF_8)).optLong("exp", 0L)
        exp <= 0L || exp <= (System.currentTimeMillis() / 1000L) + withinSeconds
    }.getOrDefault(true)
}

fun Throwable.isExpiredJwt(): Boolean {
    val text = message.orEmpty()
    return text.contains("JWT expired", ignoreCase = true) ||
        text.contains("token is expired", ignoreCase = true) ||
        text.contains("invalid jwt", ignoreCase = true)
}
