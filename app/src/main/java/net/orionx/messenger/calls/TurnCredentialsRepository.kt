package net.orionx.messenger.calls

import net.orionx.messenger.cloud.SupabaseConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches short-lived Xirsys TURN credentials from a Supabase Edge Function.
 * The Xirsys ident and secret must never be embedded in the Android application.
 */
class TurnCredentialsRepository {
    fun fetch(accessToken: String): TurnCredentialBundle {
        val connection = (URL("${SupabaseConfig.URL}/functions/v1/turn-credentials")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.use { it.write("{}".toByteArray()) }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        if (code !in 200..299) error("TURN credentials HTTP $code: $text")

        val root = JSONObject(text)
        val servers = root.optJSONArray("iceServers") ?: JSONArray()
        val parsed = buildList {
            for (index in 0 until servers.length()) {
                val item = servers.optJSONObject(index) ?: continue
                val raw = item.opt("urls") ?: item.opt("url") ?: continue
                val urls = when (raw) {
                    is JSONArray -> buildList {
                        for (urlIndex in 0 until raw.length()) raw.optString(urlIndex).takeIf { it.isNotBlank() }?.let(::add)
                    }
                    is String -> listOf(raw).filter { it.isNotBlank() }
                    else -> emptyList()
                }
                if (urls.isEmpty()) continue
                add(
                    IceServer(
                        urls = urls,
                        username = item.optString("username"),
                        credential = item.optString("credential"),
                    ),
                )
            }
        }
        val fallbackExpiry = System.currentTimeMillis() / 1000L + 3000L
        val expiresAt = when {
            root.has("expiresAt") -> root.optLong("expiresAt", fallbackExpiry)
            root.has("refreshAfter") -> root.optLong("refreshAfter", fallbackExpiry)
            else -> fallbackExpiry
        }
        return TurnCredentialBundle(parsed, expiresAt)
    }
}
