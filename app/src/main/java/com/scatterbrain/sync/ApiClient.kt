package com.scatterbrain.sync

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    // Some Cloudflare fronting blocks non-browser user agents (error 1010).
    // Present as a browser; timeouts generous for bulk uploads.
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun jsonReq(url: String, body: String, token: String?): Request {
        val b = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
        if (token != null) b.addHeader("Authorization", "Bearer $token")
        return b.build()
    }

    /** Returns token, or throws with a useful message. */
    fun login(ctx: Context): String {
        val url = Prefs.server(ctx).trimEnd('/') + "/api/v2/login"
        val body = JSONObject().put("username", Prefs.username(ctx))
            .put("password", Prefs.password(ctx)).put("fcmToken", "")
        http.newCall(jsonReq(url, body.toString(), null)).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("Login HTTP ${resp.code}: ${txt.take(120)}")
            val tok = JSONObject(txt).optString("token", "")
            if (tok.isEmpty()) throw RuntimeException("Login response had no token: ${txt.take(120)}")
            return tok
        }
    }

    /** Returns null on success, or an error string. */
    fun sync(ctx: Context, method: String, payload: String, token: String): String? {
        val err = postSync(ctx, method, payload, token)
        if (err == null) return null
        // Server tokens expire; an expired token must never stall a type.
        // Re-login with the stored credentials and retry the chunk once.
        val invalid = err.contains("invalid token", true) ||
                err.startsWith("HTTP 401") || err.startsWith("HTTP 403")
        if (!invalid) return err
        return try {
            val fresh = login(ctx)
            Prefs.setToken(ctx, fresh)
            postSync(ctx, method, payload, fresh)
        } catch (e: Exception) {
            "Re-login failed: ${e.message ?: e.javaClass.simpleName} (original: $err)"
        }
    }

    private fun postSync(ctx: Context, method: String, payload: String, token: String): String? {
        val url = Prefs.server(ctx).trimEnd('/') + "/api/v2/sync/" + method
        http.newCall(jsonReq(url, body(payload), token)).execute().use { resp ->
            if (resp.isSuccessful) return null
            val txt = resp.body?.string() ?: ""
            return "HTTP ${resp.code} on $method: ${txt.take(160)}"
        }
    }

    private fun body(payload: String): String =
        JSONObject().put("data", org.json.JSONArray(payload)).toString()
}
