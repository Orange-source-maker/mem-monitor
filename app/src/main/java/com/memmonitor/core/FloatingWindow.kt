package com.memmonitor.core

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import com.memmonitor.util.Format
import kotlin.math.abs

/**
 * 悬浮窗：小胶囊实时显示「已用百分比 · 已用GB」。
 * - 按住任意位置即可拖动
 * - 松手自动吸附到左/右屏幕边缘（类似系统悬浮球），位置持久化
 * - 轻点弹出确认框关闭悬浮窗
 * 需要用户授予「显示在其他应用上层」权限。
 */
object FloatingWindow {

    private var view: TextView? = null
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null

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
            // 恢复上次位置；首次使用默认贴右边缘
            val savedX = Prefs.floatingX(appCtx)
            val savedY = Prefs.floatingY(appCtx)
            if (savedX >= 0 && savedY >= 0) {
                x = savedX; y = savedY
            } else {
                x = appCtx.resources.displayMetrics.widthPixels - dp(appCtx, 110)
                y = dp(appCtx, 200)
            }
        }

        val touchSlop = dp(appCtx, 6)
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animator?.cancel()
                    downRawX = e.rawX; downRawY = e.rawY
                    startX = p.x; startY = p.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val dm = appCtx.resources.displayMetrics
                        val maxX = (dm.widthPixels - tv.width).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - tv.height).coerceAtLeast(0)
                        p.x = (startX + dx.toInt()).coerceIn(0, maxX)
                        p.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        try { manager.updateViewLayout(tv, p) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge(appCtx, manager, tv, p)
                    } else {
                        confirmClose(appCtx)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) snapToEdge(appCtx, manager, tv, p)
                    true
                }
                else -> false
            }
        }

        return try {
            manager.addView(tv, p)
            view = tv; wm = manager; params = p
            MonitorService.lastSample?.let { update(appCtx, it) }
            // 首次显示后按实际宽度校正一次边缘位置
            tv.post {
                if (Prefs.floatingX(appCtx) < 0) snapToEdge(appCtx, manager, tv, p)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 松手后吸附到较近的左/右边缘，带缓动动画，并保存位置 */
    private fun snapToEdge(
        appCtx: Context,
        manager: WindowManager,
        tv: TextView,
        p: WindowManager.LayoutParams
    ) {
        val dm = appCtx.resources.displayMetrics
        val margin = dp(appCtx, 4)
        val maxX = (dm.widthPixels - tv.width).coerceAtLeast(0)
        val maxY = (dm.heightPixels - tv.height).coerceAtLeast(0)
        val centerX = p.x + tv.width / 2
        val targetX = if (centerX < dm.widthPixels / 2) margin else (maxX - margin).coerceAtLeast(0)
        val targetY = p.y.coerceIn(margin, (maxY - margin).coerceAtLeast(margin))

        animator?.cancel()
        val fromX = p.x
        val fromY = p.y
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedFraction
                p.x = fromX + ((targetX - fromX) * f).toInt()
                p.y = fromY + ((targetY - fromY) * f).toInt()
                try { manager.updateViewLayout(tv, p) } catch (_: Exception) {}
            }
            start()
        }
        Prefs.setFloatingPos(appCtx, targetX, targetY)
    }

    /** 轻点悬浮窗：确认后关闭（同步关闭设置开关，避免下次自动恢复） */
    private fun confirmClose(appCtx: Context) {
        val dialogType = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val dialog = AlertDialog.Builder(appCtx)
            .setTitle("关闭悬浮窗？")
            .setMessage("关闭后可在 App 设置中重新开启。")
            .setPositiveButton("关闭") { _, _ ->
                Prefs.setFloatingEnabled(appCtx, false)
                hide(appCtx)
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.window?.setType(dialogType)
        try { dialog.show() } catch (_: Exception) {}
    }

    fun hide(context: Context) {
        animator?.cancel()
        animator = null
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
