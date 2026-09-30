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

    // Background-run visibility: the worker persists what it did (or why it
    // failed) so the UI can show real background activity instead of "never".
    fun lastBgMs(ctx: Context): Long = sp(ctx).getLong("last_bg", 0L)
    fun setLastBgMs(ctx: Context, ms: Long) = sp(ctx).edit().putLong("last_bg", ms).apply()
    fun lastBgSummary(ctx: Context): String = sp(ctx).getString("last_bg_summary", "(no background sync has run yet)") ?: ""
    fun setLastBgSummary(ctx: Context, s: String) = sp(ctx).edit().putString("last_bg_summary", s).apply()
}
