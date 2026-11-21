package com.memmonitor.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.memmonitor.R
import com.memmonitor.core.AppMemRepository
import com.memmonitor.core.FloatingWindow
import com.memmonitor.core.MonitorService
import com.memmonitor.core.Prefs
import com.memmonitor.core.RankingCodec
import com.memmonitor.core.lastRanking
import com.memmonitor.core.lastRankingAt
import com.memmonitor.util.Format
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var tvPercent: TextView
    private lateinit var tvUsed: TextView
    private lateinit var tvAvail: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvSwap: TextView
    private lateinit var tvLowMem: TextView
    private lateinit var chart: MemoryChartView
    private lateinit var btnToggle: Button
    private lateinit var rvRanking: RecyclerView
    private lateinit var tvRankState: TextView
    private lateinit var btnShizuku: Button
    private lateinit var swFloating: SwitchCompat
    private lateinit var swBoot: SwitchCompat
    private lateinit var spSample: Spinner
    private lateinit var spRank: Spinner
    private lateinit var tvUpdatedAt: TextView

    private val adapter = RankingAdapter()

    private val sampleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                MonitorService.ACTION_SAMPLE -> renderSample()
                MonitorService.ACTION_RANKING -> renderRankingBroadcast(intent)
            }
        }
    }

    private val shizukuListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQ) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    toast("Shizuku 授权成功，排行将在下一轮刷新")
                } else {
                    toast("Shizuku 授权被拒绝")
                }
                renderShizukuState(AppMemRepository.status())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupSpinners()
        setupButtons()

        rvRanking.layoutManager = LinearLayoutManager(this)
        rvRanking.adapter = adapter

        Shizuku.addRequestPermissionResultListener(shizukuListener)

        // 冷启动：恢复上次数据
        renderSample()
        lastRanking?.let {
            adapter.submit(it)
            tvUpdatedAt.text = getString(R.string.rank_updated, Format.time(lastRankingAt))
        }
        renderShizukuState(AppMemRepository.status())
        syncToggle()
    }

    private fun bindViews() {
        tvPercent = findViewById(R.id.tv_percent)
        tvUsed = findViewById(R.id.tv_used)
        tvAvail = findViewById(R.id.tv_avail)
        tvTotal = findViewById(R.id.tv_total)
        tvSwap = findViewById(R.id.tv_swap)
        tvLowMem = findViewById(R.id.tv_low_mem)
        chart = findViewById(R.id.chart)
        btnToggle = findViewById(R.id.btn_toggle)
        rvRanking = findViewById(R.id.rv_ranking)
        tvRankState = findViewById(R.id.tv_rank_state)
        btnShizuku = findViewById(R.id.btn_shizuku)
        swFloating = findViewById(R.id.sw_floating)
        swBoot = findViewById(R.id.sw_boot)
        spSample = findViewById(R.id.sp_sample_interval)
        spRank = findViewById(R.id.sp_rank_interval)
        tvUpdatedAt = findViewById(R.id.tv_rank_updated)
    }

    private fun setupSpinners() {
        val sampleOptions = listOf("1 秒", "2 秒", "5 秒")
        val sampleValues = listOf(1000L, 2000L, 5000L)
        spSample.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sampleOptions)
        spSample.setSelection(sampleValues.indexOf(Prefs.sampleIntervalMs(this)).coerceAtLeast(0))
        spSample.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setSampleIntervalMs(this@MainActivity, sampleValues[pos])
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        val rankOptions = listOf("15 秒", "30 秒", "60 秒")
        val rankValues = listOf(15000L, 30000L, 60000L)
        spRank.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rankOptions)
        spRank.setSelection(rankValues.indexOf(Prefs.rankIntervalMs(this)).coerceAtLeast(0))
        spRank.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                Prefs.setRankIntervalMs(this@MainActivity, rankValues[pos])
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun setupButtons() {
        btnToggle.setOnClickListener {
            if (MonitorService.running) {
                MonitorService.stop(this)
                btnToggle.postDelayed({ syncToggle() }, 300)
            } else {
                ensureNotificationPermission()
                MonitorService.start(this)
                btnToggle.postDelayed({ syncToggle() }, 300)
            }
        }

        swFloating.isChecked = Prefs.floatingEnabled(this)
        swFloating.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!FloatingWindow.hasPermission(this)) {
                    toast("请先授予「显示在其他应用上层」权限")
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                    swFloating.isChecked = false
                    return@setOnCheckedChangeListener
                }
                val ok = FloatingWindow.show(this)
                Prefs.setFloatingEnabled(this, ok)
                swFloating.isChecked = ok
                if (!ok) toast("悬浮窗启动失败")
            } else {
                FloatingWindow.hide(this)
                Prefs.setFloatingEnabled(this, false)
            }
        }

        swBoot.isChecked = Prefs.bootStart(this)
        swBoot.setOnCheckedChangeListener { _, checked ->
            Prefs.setBootStart(this, checked)
            if (checked) toast("已开启开机自动监控（部分机型还需在系统设置中允许自启动）")
        }

        btnShizuku.setOnClickListener { onShizukuButton() }
    }

    private fun onShizukuButton() {
        when (val st = AppMemRepository.status()) {
            is AppMemRepository.Result.ShizukuNotInstalled -> {
                toast("请先安装 Shizuku 应用（见 README 下载说明）")
            }
            is AppMemRepository.Result.ShizukuNotRunning -> {
                toast("Shizuku 未激活：请用无线调试或连接电脑 adb 激活（见 README）")
                try {
                    packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                        startActivity(it)
                    }
                } catch (_: Exception) {
                }
            }
            is AppMemRepository.Result.PermissionDenied -> {
                AppMemRepository.requestPermission(SHIZUKU_REQ)
            }
            is AppMemRepository.Result.Ok -> toast("Shizuku 已就绪")
            is AppMemRepository.Result.Failed -> toast("Shizuku 异常：${st.reason}")
        }
    }

    private fun renderSample() {
        val s = MonitorService.lastSample
        if (s == null) {
            tvPercent.text = "--%"
            return
        }
        tvPercent.text = Format.percent(s.usedPercent)
        tvUsed.text = Format.bytes(s.usedBytes)
        tvAvail.text = Format.bytes(s.availBytes)
        tvTotal.text = Format.bytes(s.totalBytes)
        tvSwap.text = if (s.swapTotalBytes > 0)
            getString(R.string.swap_fmt, Format.bytes(s.swapTotalBytes - s.swapFreeBytes), Format.bytes(s.swapTotalBytes))
        else getString(R.string.swap_none)
        tvLowMem.visibility = if (s.lowMemory) View.VISIBLE else View.GONE
        chart.setData(MonitorService.historySnapshot())
        syncToggle()
    }

    private fun renderRankingBroadcast(intent: Intent) {
        when (intent.getStringExtra(MonitorService.EXTRA_RANKING_STATE)) {
            "ok" -> {
                val json = intent.getStringExtra(MonitorService.EXTRA_RANKING_JSON) ?: "[]"
                val apps = RankingCodec.decode(json)
                adapter.submit(apps)
                tvRankState.visibility = View.GONE
                rvRanking.visibility = View.VISIBLE
                tvUpdatedAt.text = getString(R.string.rank_updated, Format.time(System.currentTimeMillis()))
                btnShizuku.text = getString(R.string.shizuku_ready)
            }
            "not_installed" -> showRankFallback(getString(R.string.rank_no_shizuku), getString(R.string.btn_install_shizuku))
            "not_running" -> showRankFallback(getString(R.string.rank_not_running), getString(R.string.btn_activate))
            "denied" -> showRankFallback(getString(R.string.rank_denied), getString(R.string.btn_authorize))
            "failed" -> {
                val reason = intent.getStringExtra(MonitorService.EXTRA_RANKING_JSON) ?: ""
                showRankFallback(getString(R.string.rank_failed, reason), getString(R.string.btn_retry))
            }
        }
    }

    private fun showRankFallback(message: String, buttonText: String) {
        tvRankState.text = message
        tvRankState.visibility = View.VISIBLE
        rvRanking.visibility = View.GONE
        btnShizuku.text = buttonText
        tvUpdatedAt.text = ""
    }

    private fun renderShizukuState(st: AppMemRepository.Result) {
        when (st) {
            is AppMemRepository.Result.Ok -> {
                btnShizuku.text = getString(R.string.shizuku_ready)
                if (adapter.itemCount == 0) {
                    tvRankState.text = getString(R.string.rank_waiting)
                    tvRankState.visibility = View.VISIBLE
                }
            }
            is AppMemRepository.Result.ShizukuNotInstalled ->
                showRankFallback(getString(R.string.rank_no_shizuku), getString(R.string.btn_install_shizuku))
            is AppMemRepository.Result.ShizukuNotRunning ->
                showRankFallback(getString(R.string.rank_not_running), getString(R.string.btn_activate))
            is AppMemRepository.Result.PermissionDenied ->
                showRankFallback(getString(R.string.rank_denied), getString(R.string.btn_authorize))
            is AppMemRepository.Result.Failed ->
                showRankFallback(getString(R.string.rank_failed, st.reason), getString(R.string.btn_retry))
        }
    }

    private fun syncToggle() {
        btnToggle.text = if (MonitorService.running) getString(R.string.btn_stop) else getString(R.string.btn_start)
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MonitorService.ACTION_SAMPLE)
            addAction(MonitorService.ACTION_RANKING)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sampleReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sampleReceiver, filter)
        }
        renderSample()
        renderShizukuState(AppMemRepository.status())
        // 若设置了悬浮窗且服务在跑，确保悬浮窗恢复
        if (Prefs.floatingEnabled(this) && MonitorService.running) FloatingWindow.show(this)
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(sampleReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val SHIZUKU_REQ = 2001
    }
}
