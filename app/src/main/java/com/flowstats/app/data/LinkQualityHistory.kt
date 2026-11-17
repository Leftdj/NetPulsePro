package com.flowstats.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 链路质量分级。阈值基于协商速率（PHY），只是链路能力的直观参考，
 * 不代表真实吞吐——UI 文案会明确说明这一点，避免把协商速率当成实测网速。
 */
enum class LinkGrade(val label: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    FAIR("一般"),
    POOR("较差"),
    BAD("很差"),
    UNKNOWN("无数据");

    companion object {
        /** 按协商速率(Mbps)分级。阈值参考 Wi-Fi 5/6/7 常见协商档位。 */
        fun of(linkMbps: Int?): LinkGrade = when {
            linkMbps == null -> UNKNOWN
            linkMbps >= 2000 -> EXCELLENT   // Wi-Fi 7 / 6E 高协商
            linkMbps >= 1200 -> GOOD        // Wi-Fi 6 1.2G 档
            linkMbps >= 600 -> FAIR         // Wi-Fi 5 866 / Wi-Fi 6 600
            linkMbps >= 300 -> POOR         // 2.4G 或弱 5G
            else -> BAD
        }
    }
}

/**
 * 一条链路质量历史样本。
 * 每轮自动测量（约 2 秒）合成一条：协商速率来自 WifiInfo，
 * 延迟/抖动/丢包来自真实 ICMP ping。任何拿不到的字段为 null，不伪造。
 */
data class LinkQualitySample(
    val time: Long,
    val linkMbps: Int?,
    val rssi: Int?,
    val latencyMs: Double?,
    val jitterMs: Double?,
    val lossPct: Double?,
    val isWifi: Boolean,
    val grade: LinkGrade = LinkGrade.of(linkMbps)
)

/**
 * 链路质量历史：内存环形缓冲 + SharedPreferences 持久化。
 * 只存真实采样值，最多 [MAX] 条，防止无限增长。
 */
object LinkQualityHistory {

    private const val MAX = 120
    private const val PREF = "link_quality_history"
    private const val KEY = "samples"

    @Volatile
    private var cached: List<LinkQualitySample> = emptyList()

    fun load(context: Context): List<LinkQualitySample> {
        if (cached.isNotEmpty()) return cached
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        cached = runCatching { parse(raw) }.getOrElse { emptyList() }
        return cached
    }

    @Synchronized
    fun add(context: Context, sample: LinkQualitySample): List<LinkQualitySample> {
        val current = cached.toMutableList()
        current.add(0, sample) // 最新在前
        while (current.size > MAX) current.removeAt(current.lastIndex)
        cached = current
        persist(context.applicationContext, current)
        return current
    }

    @Synchronized
    fun clear(context: Context) {
        cached = emptyList()
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    private fun persist(context: Context, list: List<LinkQualitySample>) {
        val arr = JSONArray()
        for (s in list) {
            val o = JSONObject()
            o.put("t", s.time)
            if (s.linkMbps != null) o.put("l", s.linkMbps) else o.put("l", JSONObject.NULL)
            if (s.rssi != null) o.put("r", s.rssi) else o.put("r", JSONObject.NULL)
            if (s.latencyMs != null) o.put("d", s.latencyMs) else o.put("d", JSONObject.NULL)
            if (s.jitterMs != null) o.put("j", s.jitterMs) else o.put("j", JSONObject.NULL)
            if (s.lossPct != null) o.put("p", s.lossPct) else o.put("p", JSONObject.NULL)
            o.put("w", s.isWifi)
            arr.put(o)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun parse(raw: String): List<LinkQualitySample> {
        val arr = JSONArray(raw)
        val result = mutableListOf<LinkQualitySample>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val link = optIntOrNull(o, "l")
            result.add(
                LinkQualitySample(
                    time = o.optLong("t", 0L),
                    linkMbps = link,
                    rssi = optIntOrNull(o, "r"),
                    latencyMs = optDoubleOrNull(o, "d"),
                    jitterMs = optDoubleOrNull(o, "j"),
                    lossPct = optDoubleOrNull(o, "p"),
                    isWifi = o.optBoolean("w", true),
                    grade = LinkGrade.of(link)
                )
            )
        }
        return result
    }

    private fun optIntOrNull(o: JSONObject, key: String): Int? =
        if (o.isNull(key)) null else o.optInt(key)

    private fun optDoubleOrNull(o: JSONObject, key: String): Double? =
        if (o.isNull(key)) null else o.optDouble(key)
}
