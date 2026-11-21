package com.memmonitor.core

import android.content.Context

/** 轻量设置存储（SharedPreferences） */
object Prefs {
    private const val FILE = "mem_monitor_prefs"
    private const val KEY_SAMPLE_INTERVAL = "sample_interval_ms"
    private const val KEY_RANK_INTERVAL = "rank_interval_ms"
    private const val KEY_FLOATING = "floating_enabled"
    private const val KEY_BOOT = "boot_start"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 整机采样间隔：1/2/5 秒，默认 2 秒 */
    fun sampleIntervalMs(context: Context): Long =
        sp(context).getLong(KEY_SAMPLE_INTERVAL, 2000L)

    fun setSampleIntervalMs(context: Context, ms: Long) =
        sp(context).edit().putLong(KEY_SAMPLE_INTERVAL, ms).apply()

    /** 各应用排行间隔：15/30/60 秒，默认 30 秒（dumpsys 开销大，不宜过密） */
    fun rankIntervalMs(context: Context): Long =
        sp(context).getLong(KEY_RANK_INTERVAL, 30000L)

    fun setRankIntervalMs(context: Context, ms: Long) =
        sp(context).edit().putLong(KEY_RANK_INTERVAL, ms).apply()

    fun floatingEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_FLOATING, false)

    fun setFloatingEnabled(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_FLOATING, enabled).apply()

    fun bootStart(context: Context): Boolean =
        sp(context).getBoolean(KEY_BOOT, false)

    fun setBootStart(context: Context, enabled: Boolean) =
        sp(context).edit().putBoolean(KEY_BOOT, enabled).apply()
}
