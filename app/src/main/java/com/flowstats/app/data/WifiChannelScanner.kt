package com.flowstats.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个被扫描到的 Wi-Fi AP。全部字段来自 WifiManager.getScanResults()，
 * 不推算频谱、不伪造信号；机型/系统可能隐藏部分字段，拿不到就是 null。
 */
data class ApInfo(
    val ssid: String,
    val bssid: String,
    val channel: Int?,
    val frequencyMhz: Int,
    val band: String,
    val rssi: Int,
    val widthMhz: Int?,
    val security: String?,
    val isCurrent: Boolean
)

/** 单个信道的统计：数量、信号、重叠压力 */
data class ChannelStat(
    val channel: Int,
    val band: String,
    val count: Int,
    val strongestRssi: Int,
    val avgRssi: Int,
    val widthMhz: Int?,
    /** 重叠压力：邻近 AP 的信号权重之和（含本信道），仅用于排序，不是实测干扰强度 */
    val overlapScore: Double
) {
    /** 拥挤度 0..100，仅用于条形长度；刻度为 100 分 */
    val crowding: Int get() = (overlapScore * 100 / 160.0).toInt().coerceIn(0, 100)
}

/** 一次扫描及其分析结果 */
data class WifiScanReport(
    val time: Long = 0L,
    val aps: List<ApInfo> = emptyList(),
    val channels: List<ChannelStat> = emptyList(),
    val currentChannel: Int? = null,
    val recommendedChannel: Int? = null,
    val bandCounts: Map<String, Int> = emptyMap(),
    /** 提示信息：扫描受限、使用缓存等 */
    val message: String? = null,
    val error: String? = null
)

/** 历史扫描摘要：只存真实扫描得到的数量和时点 */
data class ScanSummary(
    val time: Long,
    val apCount: Int,
    val currentChannel: Int?,
    val recommendedChannel: Int?
)

/**
 * Wi-Fi 信道分析仪（AP 扫描分析，不是频谱仪）。
 *
 * 说明：
 * - 数据来自 WifiManager 扫描结果，Android 不允许普通应用读取完整射频频谱；
 * - 系统限制扫描频率（通常前台几分钟内数次），频繁请求会被节流，UI 会显示结果时间；
 * - 2.4 GHz 的 20 MHz 信道每 5 个编号重叠（1/6/11 互不重叠），5/6 GHz 按实际频宽判断。
 */
object WifiChannelScanner {

    private const val PREF = "wifi_channel_history"
    private const val KEY = "summaries"
    private const val MAX_HISTORY = 24

    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, "android.permission.NEARBY_WIFI_DEVICES"
        ) == PackageManager.PERMISSION_GRANTED
        return fine || nearby
    }

    /**
     * 读取扫描缓存；同时发起一次刷新请求（结果通常下一次才可用，不阻塞等待）。
     * 权限不足或系统禁用扫描时返回 error，不编造 AP 列表。
     */
    fun scan(context: Context, currentBssid: String?): WifiScanReport {
        if (!hasPermission(context)) {
            return WifiScanReport(error = "需要位置或「附近设备」权限才能读取 Wi-Fi 扫描结果")
        }
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return WifiScanReport(error = "设备未提供 Wi-Fi 服务")
        val results = runCatching { wm.scanResults }.getOrNull()
            ?: return WifiScanReport(error = "系统未返回扫描结果（可能被厂商限制）")
        val requested = runCatching { wm.startScan() }.getOrDefault(false)
        val aps = results.mapNotNull { it.toApInfo(currentBssid) }
        if (aps.isEmpty()) return WifiScanReport(error = "扫描结果为空，请稍后重试或靠近路由器")

        val current = aps.firstOrNull { it.isCurrent }?.channel
        val channels = summarize(aps)
        val recommended = recommend(channels, current)
        val message = buildString {
            if (!requested) append("系统已限制扫描频率，显示的是最近一次系统扫描缓存。")
            else append("已请求刷新扫描，结果可能延迟到下一轮出现。")
            append("扫描时间：").append(formatTime(results.maxOfOrNull { it.timestamp } ?: 0L))
        }
        return WifiScanReport(
            time = results.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis(),
            aps = aps.sortedBy { it.rssi },
            channels = channels,
            currentChannel = current,
            recommendedChannel = recommended,
            bandCounts = aps.groupingBy { it.band }.eachCount(),
            message = message
        )
    }

    private fun formatTime(ts: Long): String {
        val d = java.util.Date(ts)
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(d)
    }

    private fun ScanResult.toApInfo(currentBssid: String?): ApInfo? {
        val ch = channelOf(frequency) ?: return null
        val band = bandOf(frequency) ?: return null
        val level = level.takeIf { it in -127..0 } ?: return null
        return ApInfo(
            ssid = SSID?.takeIf { it.isNotBlank() } ?: "[隐藏网络]",
            bssid = BSSID ?: "",
            channel = ch,
            frequencyMhz = frequency,
            band = band,
            rssi = level,
            widthMhz = widthOf(this),
            security = securityOf(capabilities),
            isCurrent = currentBssid != null && BSSID.equals(currentBssid, ignoreCase = true)
        )
    }

    fun channelOf(freq: Int): Int? = when {
        freq in 2412..2472 -> (freq - 2407) / 5
        freq == 2484 -> 14
        freq in 5160..5895 -> (freq - 5000) / 5
        freq in 5955..7125 -> (freq - 5950) / 5
        else -> null
    }

    private fun bandOf(freq: Int): String? = when (freq) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5925 -> "5 GHz"
        in 5926..7125 -> "6 GHz"
        else -> null
    }

    private fun widthOf(r: ScanResult): Int? = when (r.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
        5 -> 320
        else -> null
    }

    private fun securityOf(cap: String?): String? {
        val c = cap.orEmpty()
        return when {
            c.contains("WPA3") -> "WPA3"
            c.contains("WPA2") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            c.contains("ESS") -> "开放"
            else -> null
        }
    }

    /** 信号权重：RSSI 越强权值越高，仅用于比较哪个信道更拥挤 */
    private fun weight(rssi: Int): Double = (rssi + 100).coerceIn(0, 75).toDouble()

    private fun summarize(aps: List<ApInfo>): List<ChannelStat> {
        val byChannel = aps.groupBy { it.channel ?: -1 }
        return byChannel.filterKeys { it > 0 }.map { (ch, list) ->
            val band = list.first().band
            val self = list.sumOf { weight(it.rssi) }
            // 2.4GHz 20MHz 信道每 5 个编号重叠（1 与 2~5 重叠，与 6 不重叠）
            val overlap = byChannel.entries.sumOf { (other, others) ->
                if (other <= 0) 0.0
                else if (isOverlap(band, ch, other)) others.sumOf { weight(it.rssi) } else 0.0
            }
            ChannelStat(
                channel = ch,
                band = band,
                count = list.size,
                strongestRssi = list.maxOf { it.rssi },
                avgRssi = (list.sumOf { it.rssi } / list.size.toDouble()).toInt(),
                widthMhz = list.mapNotNull { it.widthMhz }.maxOrNull(),
                overlapScore = overlap + self * 0.35
            )
        }.sortedWith(compareBy({ bandOrder(it.band) }, { it.channel }))
    }

    private fun bandOrder(band: String): Int = when (band) {
        "2.4 GHz" -> 0
        "5 GHz" -> 1
        "6 GHz" -> 2
        else -> 3
    }

    private fun isOverlap(band: String, a: Int, b: Int): Boolean {
        if (a == b) return true
        if (band != "2.4 GHz") return false
        return kotlin.math.abs(a - b) < 5
    }

    /**
     * 推荐信道：2.4GHz 只在 1/6/11 三个互不重叠的信道里选压力最低的；
     * 5/6GHz 在扫描到的信道里选压力最低的。当前信道压力已经最低时直接建议保持。
     */
    private fun recommend(channels: List<ChannelStat>, current: Int?): Int? {
        val candidates = channels.filter { it.band == "2.4 GHz" && it.channel in listOf(1, 6, 11) }
            .ifEmpty { channels.filter { it.band == "2.4 GHz" } }
            .ifEmpty { channels }
        val best = candidates.minByOrNull { it.overlapScore } ?: return null
        val currentStat = channels.firstOrNull { it.channel == current }
        if (currentStat != null && currentStat.overlapScore <= best.overlapScore * 1.1) return current
        return best.channel
    }

    // ── 历史 ─────────────────────────────────────────────

    fun loadHistory(context: Context): List<ScanSummary> {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ScanSummary(
                    time = o.optLong("t", 0L),
                    apCount = o.optInt("n", 0),
                    currentChannel = if (o.isNull("c")) null else o.optInt("c"),
                    recommendedChannel = if (o.isNull("r")) null else o.optInt("r")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun record(context: Context, report: WifiScanReport): List<ScanSummary> {
        if (report.aps.isEmpty()) return loadHistory(context)
        val item = ScanSummary(
            time = report.time,
            apCount = report.aps.size,
            currentChannel = report.currentChannel,
            recommendedChannel = report.recommendedChannel
        )
        val list = (loadHistory(context) + item).takeLast(MAX_HISTORY)
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("t", it.time); o.put("n", it.apCount)
            o.put("c", it.currentChannel ?: JSONObject.NULL)
            o.put("r", it.recommendedChannel ?: JSONObject.NULL)
            arr.put(o)
        }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        return list
    }

    fun clearHistory(context: Context) {
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
