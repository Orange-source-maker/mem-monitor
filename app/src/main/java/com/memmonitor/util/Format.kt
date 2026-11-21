package com.memmonitor.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    fun bytes(b: Long): String {
        val gb = b / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1.0) String.format(Locale.CHINA, "%.2f GB", gb)
        else String.format(Locale.CHINA, "%.0f MB", b / 1024.0 / 1024.0)
    }

    fun kb(kb: Long): String = bytes(kb * 1024)

    fun percent(p: Float): String = String.format(Locale.CHINA, "%.1f%%", p)

    fun time(ts: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(ts))
}
