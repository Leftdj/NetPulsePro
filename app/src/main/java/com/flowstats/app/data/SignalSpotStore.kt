package com.flowstats.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个位置的 Wi-Fi 信号快照。保存的是那段时间真实采样的 RSSI 统计，
 * 不是估算；样本不足时不生成记录。
 */
data class SignalSpot(
    val time: Long,
    val label: String,
    val ssid: String?,
    val avgRssi: Int,
    val minRssi: Int,
    val count: Int
)

/** 不同位置的信号质量记录，本地持久化，最多 20 条 */
object SignalSpotStore {
    private const val PREF = "signal_spots"
    private const val KEY = "spots"
    private const val MAX = 20

    fun load(context: Context): List<SignalSpot> {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SignalSpot(
                    time = o.optLong("t", 0L),
                    label = o.optString("l", "未命名"),
                    ssid = if (o.isNull("s")) null else o.optString("s"),
                    avgRssi = o.optInt("a", 0),
                    minRssi = o.optInt("m", 0),
                    count = o.optInt("c", 0)
                )
            }
        }.getOrElse { emptyList() }
    }

    fun add(context: Context, spot: SignalSpot): List<SignalSpot> {
        val list = (listOf(spot) + load(context)).take(MAX)
        persist(context, list)
        return list
    }

    fun remove(context: Context, time: Long): List<SignalSpot> {
        val list = load(context).filterNot { it.time == time }
        persist(context, list)
        return list
    }

    private fun persist(context: Context, list: List<SignalSpot>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("t", it.time); o.put("l", it.label)
            o.put("s", it.ssid ?: JSONObject.NULL)
            o.put("a", it.avgRssi); o.put("m", it.minRssi); o.put("c", it.count)
            arr.put(o)
        }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
