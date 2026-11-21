package com.memmonitor.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.memmonitor.util.Format
import kotlin.math.abs

/**
 * 悬浮窗：一个小胶囊，实时显示「已用百分比 / 已用GB」。
 * 可拖动；需要用户授予「显示在其他应用上层」权限。
 */
object FloatingWindow {

    private var view: TextView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    fun hasPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context): Boolean {
        if (!hasPermission(context)) return false
        if (view != null) return true
        val appCtx = context.applicationContext
        val manager = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val tv = TextView(appCtx).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(appCtx, 12), dp(appCtx, 6), dp(appCtx, 12), dp(appCtx, 6))
            background = GradientDrawable().apply {
                cornerRadius = dp(appCtx, 16).toFloat()
                setColor(Color.argb(210, 24, 28, 38))
            }
            text = "内存 --%"
        }

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(appCtx, 16)
            y = dp(appCtx, 120)
        }

        // 拖动支持
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    p.x = startX + dx.toInt()
                    p.y = startY + dy.toInt()
                    try { manager.updateViewLayout(tv, p) } catch (_: Exception) {}
                    true
                }
                else -> moved
            }
        }

        return try {
            manager.addView(tv, p)
            view = tv; wm = manager; params = p
            MonitorService.lastSample?.let { update(appCtx, it) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        try {
            (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .removeView(v)
        } catch (_: Exception) {
        }
        view = null; wm = null; params = null
    }

    fun update(context: Context, s: MemorySampler.Sample) {
        val v = view ?: return
        v.post {
            v.text = "${Format.percent(s.usedPercent)} · ${Format.bytes(s.usedBytes)}"
            val bg = v.background as? GradientDrawable
            val color = when {
                s.usedPercent >= 90f -> Color.argb(220, 160, 40, 40)
                s.usedPercent >= 75f -> Color.argb(220, 150, 100, 20)
                else -> Color.argb(210, 24, 28, 38)
            }
            bg?.setColor(color)
        }
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
