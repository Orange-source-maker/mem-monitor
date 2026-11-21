package com.memmonitor.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import com.memmonitor.R
import com.memmonitor.ui.MainActivity
import com.memmonitor.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 常驻前台服务：按设定间隔采样整机内存，更新通知栏，并向 UI 广播数据。
 * 各应用排行采样开销大，按独立较长间隔执行。
 */
class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "mem_monitor"
        const val NOTIF_ID = 1001

        const val ACTION_START = "com.memmonitor.START"
        const val ACTION_STOP = "com.memmonitor.STOP"
        const val ACTION_SAMPLE = "com.memmonitor.SAMPLE"
        const val ACTION_RANKING = "com.memmonitor.RANKING"

        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_RANKING_STATE = "ranking_state"
        const val EXTRA_RANKING_JSON = "ranking_json"

        @Volatile
        var running: Boolean = false
            private set

        /** 最近一次整机采样（UI 冷启动时直接读取） */
        @Volatile
        var lastSample: MemorySampler.Sample? = null
            private set

        /** 内存曲线历史（最多 300 点，进程内共享） */
        val history = ArrayDeque<MemorySampler.Sample>()
        private const val HISTORY_MAX = 300

        fun start(context: Context) {
            val i = Intent(context, MonitorService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            )
        }

        fun pushSample(s: MemorySampler.Sample) {
            lastSample = s
            synchronized(history) {
                history.addLast(s)
                while (history.size > HISTORY_MAX) history.removeFirst()
            }
        }

        fun historySnapshot(): List<MemorySampler.Sample> = synchronized(history) { history.toList() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sampleJob: Job? = null
    private var rankJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!running) {
                    running = true
                    startInForeground()
                    startLoops()
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = buildNotification(lastSample)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            // Android 13 及以下无需指定类型（清单中已声明 specialUse 供 14+ 使用）
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startLoops() {
        sampleJob?.cancel()
        sampleJob = scope.launch {
            while (isActive) {
                val interval = Prefs.sampleIntervalMs(this@MonitorService)
                val s = withContext(Dispatchers.IO) { MemorySampler.sample(this@MonitorService) }
                pushSample(s)
                updateNotification(s)
                sendBroadcast(Intent(ACTION_SAMPLE).setPackage(packageName))
                FloatingWindow.update(this@MonitorService, s)
                delay(interval)
            }
        }

        rankJob?.cancel()
        rankJob = scope.launch {
            while (isActive) {
                val interval = Prefs.rankIntervalMs(this@MonitorService)
                val result = withContext(Dispatchers.IO) {
                    AppMemRepository.fetchRanking(this@MonitorService)
                }
                broadcastRanking(result)
                delay(interval)
            }
        }
    }

    private fun broadcastRanking(result: AppMemRepository.Result) {
        val i = Intent(ACTION_RANKING).setPackage(packageName)
        when (result) {
            is AppMemRepository.Result.Ok -> {
                i.putExtra(EXTRA_RANKING_STATE, "ok")
                i.putExtra(EXTRA_RANKING_JSON, RankingCodec.encode(result.apps))
                lastRanking = result.apps
                lastRankingAt = result.sampledAt
            }
            is AppMemRepository.Result.ShizukuNotInstalled -> i.putExtra(EXTRA_RANKING_STATE, "not_installed")
            is AppMemRepository.Result.ShizukuNotRunning -> i.putExtra(EXTRA_RANKING_STATE, "not_running")
            is AppMemRepository.Result.PermissionDenied -> i.putExtra(EXTRA_RANKING_STATE, "denied")
            is AppMemRepository.Result.Failed -> {
                i.putExtra(EXTRA_RANKING_STATE, "failed")
                i.putExtra(EXTRA_RANKING_JSON, result.reason)
            }
        }
        sendBroadcast(i)
    }

    private fun buildNotification(s: MemorySampler.Sample?): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (s == null) getString(R.string.notif_starting)
        else getString(
            R.string.notif_text,
            Format.percent(s.usedPercent),
            Format.bytes(s.usedBytes),
            Format.bytes(s.totalBytes),
            Format.bytes(s.availBytes)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        builder
            .setSmallIcon(R.drawable.ic_stat_memory)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            // 进度条：满条 = 本机总内存，蓝色部分长度 = 已用占比
            .setColor(Color.parseColor("#3D8BFD"))
            .addAction(0, getString(R.string.action_stop), stop)
        if (s != null) {
            builder.setProgress(100, s.usedPercent.toInt().coerceIn(0, 100), false)
        }
        return builder.build()
    }

    private fun updateNotification(s: MemorySampler.Sample) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(s))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        running = false
        sampleJob?.cancel()
        rankJob?.cancel()
        scope.cancel()
        FloatingWindow.hide(this)
        super.onDestroy()
    }
}

/** 最近一次排行结果（UI 冷启动读取） */
@Volatile
var lastRanking: List<AppMemRepository.AppMem>? = null

@Volatile
var lastRankingAt: Long = 0L
