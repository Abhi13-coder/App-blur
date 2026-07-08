package com.abhiram.appblur

import android.content.Context

object Prefs {
    private const val NAME = "appblur_prefs"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getTimeoutSeconds(ctx: Context): Int = sp(ctx).getInt("timeout_seconds", 8)
    fun setTimeoutSeconds(ctx: Context, v: Int) = sp(ctx).edit().putInt("timeout_seconds", v).apply()

    fun isWatchingEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("watching_enabled", true)
    fun setWatchingEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("watching_enabled", v).apply()

    fun getButtonX(ctx: Context): Int = sp(ctx).getInt("button_x", 0)
    fun getButtonY(ctx: Context): Int = sp(ctx).getInt("button_y", 300)
    fun setButtonPos(ctx: Context, x: Int, y: Int) =
        sp(ctx).edit().putInt("button_x", x).putInt("button_y", y).apply()

    fun getExcludedApps(ctx: Context): MutableSet<String> =
        HashSet(sp(ctx).getStringSet("excluded_apps", emptySet()) ?: emptySet())

    fun setExcludedApps(ctx: Context, packages: Set<String>) =
        sp(ctx).edit().putStringSet("excluded_apps", packages).apply()

    fun getOpacityPercent(ctx: Context): Int = sp(ctx).getInt("opacity_percent", 95)
    fun setOpacityPercent(ctx: Context, v: Int) = sp(ctx).edit().putInt("opacity_percent", v).apply()

    val TIMEOUT_PRESETS = intArrayOf(5, 10, 15, 20, 30)

    fun nextTimeout(current: Int): Int {
        val idx = TIMEOUT_PRESETS.indexOf(current)
        val nextIdx = if (idx == -1) 0 else (idx + 1) % TIMEOUT_PRESETS.size
        return TIMEOUT_PRESETS[nextIdx]
    }
}
