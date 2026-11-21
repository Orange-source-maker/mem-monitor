package com.memmonitor.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后按用户设置恢复监控（默认关闭，需用户在设置中开启） */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.bootStart(context)) {
            MonitorService.start(context)
        }
    }
}
