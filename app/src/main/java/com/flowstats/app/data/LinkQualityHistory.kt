package com.flowstats.app.data

import android.content.Context
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * 链路质量分级。
 *
 * 协商速率（PHY）能到多少，取决于频段与频宽：2.4 GHz 通常 20/40 MHz，理论上限约 344/688 Mbps；
 * 5 GHz 通常 80/160 MHz，理论上限约 1441/2882 Mbps。同一个 344 Mbps，在 2.4 GHz 20 MHz 下
 * 已经是满档，放到 5 GHz 80 MHz 却只有四分之一——所以不能用一套绝对 Mbps 阈值套所有频段。
 *
 * 这里统一按「协商速率 ÷ 该配置参考上限」的比例评级，并把频段/频宽一起记录，
 * 这样切到 2.4G 或 5G 时评级都仍然成立。参考上限只是链路能力的刻度，不代表实测吞吐。
 */
enum class LinkGrade(val label: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    FAIR("一般"),
    POOR("较差"),
    BAD("很差"),
    UNKNOWN("无数据");

    companion object {
        /**
         * 按协商速率占参考上限的比例分级。
         * 拿不到参考上限（无频宽/频段信息）时退回通用绝对档位，兼容早期记录。
         */
        fun of(linkMbps: Int?, ceilingMbps: Int?): LinkGrade {
            if (linkMbps == null || linkMbps <= 0) return UNKNOWN
            if (ceilingMbps == null || ceilingMbps <= 0) {
                return when {
                    linkMbps >= 2000 -> EXCELLENT
                    linkMbps >= 1200 -> GOOD
                    linkMbps >= 600 -> FAIR
                    linkMbps >= 300 -> POOR
                    else -> BAD
                }
            }
            val pct = linkMbps.toDouble() / ceilingMbps
            return when {
                pct >= 0.85 -> EXCELLENT
                pct >= 0.65 -> GOOD
                pct >= 0.45 -> FAIR
                pct >= 0.25 -> POOR
                else -> BAD
            }
        }
    }
}

/**
 * 频宽/频段 → 协商速率参考上限。
 * 取 802.11ax/be 单流 4096-QAM 的理论 PHY 速率并取整，作为「满档」刻度。
 */
object LinkGrading {

    /** 仅按频宽推导上限（Mbps） */
    fun ceilingByWidth(widthMhz: Int?): Int? = when {
        widthMhz == null -> null
        widthMhz >= 320 -> 5764
        widthMhz >= 160 -> 2882
        widthMhz >= 80 -> 1441
        widthMhz >= 40 -> 688
        widthMhz >= 20 -> 344
        else -> null
    }

    /**
     * 参考上限：优先用真实频宽；拿不到频宽（未授予定位权限）时，按该频段常见最大频宽取参考档；
     * 频段也没有则返回 null，评级退回通用绝对档位（不自造刻度）。
     *
     * @param deviceMaxMbps 设备自身上限（WifiInfo.maxSupportedRxLinkSpeedMbps）。
     *        设备硬件更低时以设备为准——老设备在 80 MHz 跑满 866 Mbps 不该被判成「一般」。
     */
    fun referenceCeiling(widthMhz: Int?, band: String?, deviceMaxMbps: Int? = null): Int? {
        val base = ceilingByWidth(widthMhz) ?: when {
            band == null -> null
            band.contains("2.4") -> ceilingByWidth(40)
            band.contains("6") -> ceilingByWidth(160)
            else -> ceilingByWidth(80)
        } ?: return null
        val cap = deviceMaxMbps?.takeIf { it > 0 }
        return if (cap != null && cap < base) cap else base
    }
}

/**
 * 一条链路质量历史样本。
 * 每轮自动测量（约 2 秒）合成一条：协商速率来自 WifiInfo，延迟/抖动/丢包来自真实 ICMP ping。
 * 任何拿不到的字段为 null，不伪造。
 */
data class LinkQualitySample(
    val time: Long,
    val linkMbps: Int?,
    val rssi: Int?,
    val latencyMs: Double?,
    val jitterMs: Double?,
    val lossPct: Double?,
    val isWifi: Boolean,
    /** 采样时的频段，如 "2.4 GHz" / "5 GHz"；旧记录为 null */
    val band: String? = null,
    /** 采样时的频宽，如 20/40/80/160；旧记录为 null */
    val widthMhz: Int? = null,
    /** 采样时的参考上限，用于跨频段比较与百分比展示 */
    val ceilingMbps: Int? = null,
    val grade: LinkGrade = LinkGrade.of(linkMbps, ceilingMbps)
) {
    /** 协商速率占参考上限的百分比（0..100），仅用于解释评级 */
    val pctOfCeiling: Int?
        get() {
            val link = linkMbps ?: return null
            val ceil = ceilingMbps?.takeIf { it > 0 } ?: return null
            return (link * 100.0 / ceil).roundToInt().coerceIn(0, 100)
        }

    /** 频段短标签：2.4G / 5G / 6G */
    val bandTag: String?
        get() = band?.let {
            when {
                it.contains("2.4") -> "2.4G"
                it.contains("6") -> "6G"
                it.contains("5") -> "5G"
                else -> null
            }
        }
}

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
        cached = runCatching { backfill(parse(raw)) }.getOrElse { emptyList() }
        return cached
    }

    /**
     * 老版本记录没有频段/频宽字段，评级只能退回绝对档位（2.4G 会被误判成「很差」）。
     * 历史最多只覆盖最近几分钟，同一网络下频段不会频繁变，因此让缺失项沿用相邻较新记录的频段/上限，
     * 再用新口径重算评级。只是让同一段历史的判据一致，不改写任何实测数值。
     */
    private fun backfill(list: List<LinkQualitySample>): List<LinkQualitySample> {
        var band: String? = null
        var width: Int? = null
        var ceiling: Int? = null
        return list.map { s ->
            if (s.band != null) band = s.band
            if (s.widthMhz != null) width = s.widthMhz
            if (s.ceilingMbps != null) ceiling = s.ceilingMbps
            if (s.ceilingMbps != null && s.band != null) s
            else s.copy(
                band = s.band ?: band,
                widthMhz = s.widthMhz ?: width,
                ceilingMbps = s.ceilingMbps ?: ceiling,
                grade = LinkGrade.of(s.linkMbps, s.ceilingMbps ?: ceiling)
            )
        }
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
            o.put("l", s.linkMbps ?: JSONObject.NULL)
            o.put("r", s.rssi ?: JSONObject.NULL)
            o.put("d", s.latencyMs ?: JSONObject.NULL)
            o.put("j", s.jitterMs ?: JSONObject.NULL)
            o.put("p", s.lossPct ?: JSONObject.NULL)
            o.put("w", s.isWifi)
            o.put("b", s.band ?: JSONObject.NULL)
            o.put("cw", s.widthMhz ?: JSONObject.NULL)
            o.put("ce", s.ceilingMbps ?: JSONObject.NULL)
            o.put("g", s.grade.name)
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
            val ceiling = optIntOrNull(o, "ce")
            // 早期记录没有频段/频宽/上限，也没有存评级：按旧口径重算（上限为空 → 通用档位）
            val grade = o.optString("g", "").takeIf { it.isNotBlank() }
                ?.let { name -> LinkGrade.entries.firstOrNull { it.name == name } }
                ?: LinkGrade.of(link, ceiling)
            result.add(
                LinkQualitySample(
                    time = o.optLong("t", 0L),
                    linkMbps = link,
                    rssi = optIntOrNull(o, "r"),
                    latencyMs = optDoubleOrNull(o, "d"),
                    jitterMs = optDoubleOrNull(o, "j"),
                    lossPct = optDoubleOrNull(o, "p"),
                    isWifi = o.optBoolean("w", true),
                    band = if (o.isNull("b")) null else o.optString("b").takeIf { it.isNotBlank() },
                    widthMhz = optIntOrNull(o, "cw"),
                    ceilingMbps = ceiling,
                    grade = grade
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
