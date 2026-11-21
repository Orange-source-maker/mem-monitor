package com.memmonitor.core

import android.app.ActivityManager
import android.content.Context
import java.io.RandomAccessFile

/**
 * 整机内存采样器。
 * 数据来源：ActivityManager.MemoryInfo（系统 API）+ /proc/meminfo（交叉补充 Swap）。
 * 无需任何特殊权限。
 */
object MemorySampler {

    data class Sample(
        val timestamp: Long,
        val totalBytes: Long,
        val availBytes: Long,
        val usedBytes: Long,
        val usedPercent: Float,
        val thresholdBytes: Long,   // 系统低内存阈值
        val lowMemory: Boolean,
        val swapTotalBytes: Long,
        val swapFreeBytes: Long
    )

    fun sample(context: Context): Sample {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val total = info.totalMem
        val avail = info.availMem
        val used = (total - avail).coerceAtLeast(0L)
        val percent = if (total > 0) used * 100f / total else 0f

        var swapTotal = 0L
        var swapFree = 0L
        try {
            RandomAccessFile("/proc/meminfo", "r").use { raf ->
                var line: String?
                var read = 0
                while (raf.readLine().also { line = it } != null && read < 60) {
                    read++
                    val l = line ?: continue
                    when {
                        l.startsWith("SwapTotal:") -> swapTotal = parseKb(l) * 1024
                        l.startsWith("SwapFree:") -> swapFree = parseKb(l) * 1024
                    }
                }
            }
        } catch (_: Exception) {
            // /proc 读取失败不影响主数据
        }

        return Sample(
            timestamp = System.currentTimeMillis(),
            totalBytes = total,
            availBytes = avail,
            usedBytes = used,
            usedPercent = percent,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            swapTotalBytes = swapTotal,
            swapFreeBytes = swapFree
        )
    }

    private fun parseKb(line: String): Long {
        // 形如 "SwapTotal:       4194300 kB"
        val parts = line.split(Regex("\\s+"))
        return parts.getOrNull(1)?.toLongOrNull() ?: 0L
    }
}
