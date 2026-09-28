package com.scatterbrain.sync

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("scattersync", Context.MODE_PRIVATE)

    fun server(ctx: Context): String =
        sp(ctx).getString("server", "https://hcgateway-api.sam-6e1.workers.dev")!!

    fun setServer(ctx: Context, v: String) = sp(ctx).edit().putString("server", v).apply()

    fun username(ctx: Context): String = sp(ctx).getString("username", "") ?: ""
    fun setUsername(ctx: Context, v: String) = sp(ctx).edit().putString("username", v).apply()

    fun password(ctx: Context): String = sp(ctx).getString("password", "") ?: ""
    fun setPassword(ctx: Context, v: String) = sp(ctx).edit().putString("password", v).apply()

    fun token(ctx: Context): String? = sp(ctx).getString("token", null)
    fun setToken(ctx: Context, v: String) = sp(ctx).edit().putString("token", v).apply()

    fun lastSyncMs(ctx: Context, type: String): Long = sp(ctx).getLong("last_$type", 0L)
    fun setLastSyncMs(ctx: Context, type: String, ms: Long) =
        sp(ctx).edit().putLong("last_$type", ms).apply()
}
