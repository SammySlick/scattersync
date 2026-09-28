package com.scatterbrain.sync

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun jsonReq(url: String, body: String, token: String?): Request {
        val b = Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
        if (token != null) b.addHeader("Authorization", "Bearer $token")
        return b.build()
    }

    fun login(ctx: Context): String? {
        val url = Prefs.server(ctx).trimEnd('/') + "/api/v2/login"
        val body = JSONObject().put("username", Prefs.username(ctx))
            .put("password", Prefs.password(ctx)).put("fcmToken", "")
        http.newCall(jsonReq(url, body.toString(), null)).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val txt = resp.body?.string() ?: return null
            return JSONObject(txt).optString("token", null)
        }
    }

    fun sync(ctx: Context, method: String, payload: String, token: String): Boolean {
        val url = Prefs.server(ctx).trimEnd('/') + "/api/v2/sync/" + method
        val body = JSONObject().put("data", org.json.JSONArray(payload)).toString()
        http.newCall(jsonReq(url, body, token)).execute().use { resp ->
            return resp.isSuccessful
        }
    }
}
