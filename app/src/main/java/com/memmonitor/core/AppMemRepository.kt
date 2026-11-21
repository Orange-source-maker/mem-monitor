package com.memmonitor.core

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 各应用内存占用排行（PSS）。
 *
 * 系统限制：Android 7+ 普通应用无法读取其他进程内存。
 * 本实现通过 Shizuku（免 root，adb 权限级）执行 dumpsys meminfo 获取真实 PSS。
 * Shizuku 未安装/未激活/未授权时返回 null，由 UI 显示降级引导，绝不编造数据。
 */
object AppMemRepository {

    data class AppMem(
        val processName: String,
        val pid: Int,
        val pssKb: Long,
        val label: String,
        val packageName: String?
    )

    sealed class Result {
        data class Ok(val apps: List<AppMem>, val sampledAt: Long) : Result()
        object ShizukuNotInstalled : Result()
        object ShizukuNotRunning : Result()
        object PermissionDenied : Result()
        data class Failed(val reason: String) : Result()
    }

    /** 快速状态检查（不执行命令），供 UI 显示当前授权状态 */
    fun status(): Result {
        return try {
            if (!Shizuku.pingBinder()) return Result.ShizukuNotRunning
            if (Shizuku.isPreV11()) return Result.Failed("Shizuku 版本过旧，请升级")
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return Result.PermissionDenied
            }
            Result.Ok(emptyList(), 0L)
        } catch (_: IllegalStateException) {
            Result.ShizukuNotInstalled
        } catch (e: Exception) {
            Result.Failed(e.message ?: "未知错误")
        }
    }

    /** 请求 Shizuku 授权（弹窗由 Shizuku 应用展示） */
    fun requestPermission(requestCode: Int) {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED &&
                !Shizuku.shouldShowRequestPermissionRationale()
            ) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 执行一次全量排行采样。耗时约 1~3 秒，必须在后台线程调用。
     * 返回按 PSS 降序的前 [limit] 个进程。
     */
    fun fetchRanking(context: Context, limit: Int = 40): Result {
        val pre = status()
        if (pre !is Result.Ok) return pre

        return try {
            val output = execDumpsys()
            val rows = parseMeminfo(output)
            if (rows.isEmpty()) return Result.Failed("未解析到进程数据（系统输出格式可能不同）")

            val pm = context.packageManager
            val labelCache = HashMap<String, Pair<String, String?>>()
            val apps = rows.sortedByDescending { it.pssKb }.take(limit).map { row ->
                val pkg = guessPackage(row.processName)
                val cached = pkg?.let { labelCache[it] }
                val (label, realPkg) = if (cached != null) cached else resolveLabel(pm, pkg, row.processName)
                if (pkg != null) labelCache[pkg] = label to realPkg
                AppMem(row.processName, row.pid, row.pssKb, label, realPkg)
            }
            Result.Ok(apps, System.currentTimeMillis())
        } catch (e: Exception) {
            Result.Failed(e.message ?: "执行失败")
        }
    }

    private data class Row(val processName: String, val pid: Int, val pssKb: Long)

    /** 通过 Shizuku 以 shell 权限执行 dumpsys meminfo */
    private fun execDumpsys(): String {
        // 官方标记 newProcess 为 deprecated（13.1.1 起）但 13.1.5 仍可用。
        // 通过反射调用以规避隐藏 API 限制；失败时给出明确错误而非崩溃。
        val process: java.lang.Process = try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            method.isAccessible = true
            method.invoke(null, arrayOf("dumpsys", "meminfo"), null, null) as java.lang.Process
        } catch (e: Exception) {
            throw IllegalStateException(
                "无法通过 Shizuku 执行命令（${e.javaClass.simpleName}）。请确认 Shizuku 已激活且版本 ≥ 11。", e
            )
        }
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.forEachLine { sb.append(it).append('\n') }
        }
        // ShizukuRemoteProcess 提供 waitForTimeout（官方源码核实）；
        // 标准 Process.waitFor(timeout) 需 API 26+，此处用反射优先走 Shizuku 自带方法。
        val finished = try {
            val m = process.javaClass.getMethod("waitForTimeout", Long::class.javaPrimitiveType, TimeUnit::class.java)
            m.invoke(process, 15L, TimeUnit.SECONDS) as Boolean
        } catch (_: Exception) {
            process.waitFor() == 0
        }
        if (!finished) {
            process.destroy()
            throw IllegalStateException("dumpsys 执行超时")
        }
        return sb.toString()
    }

    /**
     * 解析 dumpsys meminfo 的 "Total PSS by process" 段。
     * 典型行："    234,567K: com.tencent.mm (pid 12345)"
     * 也兼容 "234,567K: name (pid 1234 / activities)" 等变体。
     */
    internal fun parseMeminfo(output: String): List<Row> {
        val rows = ArrayList<Row>()
        var inSection = false
        val lineRe = Regex("^\\s*([\\d,]+)K:\\s+(.+?)\\s+\\(pid\\s+(\\d+)")
        for (raw in output.lines()) {
            val line = raw.trimEnd()
            if (!inSection) {
                if (line.contains("Total PSS by process")) inSection = true
                continue
            }
            // 段落内的有效行均以空白开头；遇到顶格的非空行即下一段落标题，结束解析
            if (line.isNotEmpty() && !line[0].isWhitespace()) {
                break
            }
            val m = lineRe.find(line) ?: continue
            val pss = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            val name = m.groupValues[2].trim()
            val pid = m.groupValues[3].toIntOrNull() ?: continue
            rows.add(Row(name, pid, pss))
        }
        return rows
    }

    private fun guessPackage(processName: String): String? {
        // "com.tencent.mm:push" -> "com.tencent.mm"；纯进程名（如 surfaceflinger）返回 null
        val base = processName.substringBefore(':')
        return if (base.contains('.')) base else null
    }

    private fun resolveLabel(
        pm: PackageManager,
        pkg: String?,
        processName: String
    ): Pair<String, String?> {
        if (pkg == null) return processName to null
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString() to pkg
        } catch (_: PackageManager.NameNotFoundException) {
            processName to null
        }
    }
}
