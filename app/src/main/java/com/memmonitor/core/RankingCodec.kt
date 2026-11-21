package com.memmonitor.core

import org.json.JSONArray
import org.json.JSONObject

/** 排行数据 <-> JSON（通过广播在进程内传递，避免 Binder 大数据限制） */
object RankingCodec {

    fun encode(apps: List<AppMemRepository.AppMem>): String {
        val arr = JSONArray()
        for (a in apps) {
            arr.put(JSONObject().apply {
                put("process", a.processName)
                put("pid", a.pid)
                put("pssKb", a.pssKb)
                put("label", a.label)
                put("pkg", a.packageName ?: JSONObject.NULL)
            })
        }
        return arr.toString()
    }

    fun decode(json: String): List<AppMemRepository.AppMem> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppMemRepository.AppMem(
                    processName = o.getString("process"),
                    pid = o.getInt("pid"),
                    pssKb = o.getLong("pssKb"),
                    label = o.getString("label"),
                    packageName = if (o.isNull("pkg")) null else o.getString("pkg")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
