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
    val isCurrent: Boolean,
    /** 是否为 DFS 信道（5 GHz 的 52–64 / 100–144，需雷达检测） */
    val isDfs: Boolean = false
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
    val overlapScore: Double,
    val isDfs: Boolean = false,
    /** 本信道 AP 的信号权重之和，用于展示计算依据 */
    val selfScore: Double = 0.0
) {
    /**
     * 相对拥挤指数 0..100：基于扫描到的 AP 数量与信号强度按邻近重叠加权得到的相对刻度，
     * 不是真实信道占用率，也不代表空口利用率。100 仅表示本次扫描样本中压力最高。
     */
    val crowding: Int get() = (overlapScore * 100 / 160.0).toInt().coerceIn(0, 100)
}

/** 扫描结果新鲜度：区分「本次新结果」与「系统历史缓存」，绝不把旧缓存当最新 */
enum class ScanFreshness(val label: String) {
    NEW("已获取新结果"),
    WAITING("等待系统扫描"),
    CACHE("使用历史缓存")
}

/** 一次扫描及其分析结果 */
data class WifiScanReport(
    val time: Long = 0L,
    val aps: List<ApInfo> = emptyList(),
    val channels: List<ChannelStat> = emptyList(),
    val currentChannel: Int? = null,
    val recommendedChannel: Int? = null,
    /** 推荐理由与备选信道，说明依据而非只给结论 */
    val recommendReason: String? = null,
    val alternatives: List<Int> = emptyList(),
    val bandCounts: Map<String, Int> = emptyMap(),
    val freshness: ScanFreshness = ScanFreshness.WAITING,
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
        val now = System.currentTimeMillis()
        val results = runCatching { wm.scanResults }.getOrNull()
            ?: return WifiScanReport(error = "系统未返回扫描结果（可能被厂商限制）")
        // 先读缓存时间戳，再请求刷新：本次读到的是上一轮扫描结果，不能当作刚刷新出来的
        val newestTimestamp = results.maxOfOrNull { it.timestamp } ?: 0L
        val requested = runCatching { wm.startScan() }.getOrDefault(false)
        val freshness = when {
            newestTimestamp <= 0L -> ScanFreshness.WAITING
            now - newestTimestamp <= 15_000L -> ScanFreshness.NEW
            else -> ScanFreshness.CACHE
        }
        val aps = results.mapNotNull { it.toApInfo(currentBssid) }
        if (aps.isEmpty()) return WifiScanReport(
            error = "扫描结果为空，请稍后重试或靠近路由器",
            freshness = freshness
        )

        val current = aps.firstOrNull { it.isCurrent }?.channel
        val channels = summarize(aps)
        val (recommended, reason, alternatives) = recommendWithReason(channels, current)
        val message = buildString {
            when (freshness) {
                ScanFreshness.NEW -> append("已获取新结果。")
                ScanFreshness.WAITING -> append("正在等待系统返回新结果，当前为已缓存数据。")
                ScanFreshness.CACHE -> append("系统限制扫描频率，显示最近一次系统扫描缓存，不是本轮新结果。")
            }
            if (!requested) append("本轮刷新请求被系统拒绝（通常因扫描过频）。")
            append("扫描时间：").append(formatTime(newestTimestamp))
            append(" · 读取时间：").append(formatTime(now))
        }
        return WifiScanReport(
            time = newestTimestamp,
            // RSSI 数值越大（越接近 0）信号越强，必须降序；相同强度再按频段、信道稳定排序
            aps = aps.sortedWith(
                compareByDescending<ApInfo> { it.rssi }
                    .thenBy { bandOrder(it.band) }
                    .thenBy { it.channel ?: 999 }
            ),
            channels = channels,
            currentChannel = current,
            recommendedChannel = recommended,
            recommendReason = reason,
            alternatives = alternatives,
            bandCounts = aps.groupingBy { it.band }.eachCount(),
            freshness = freshness,
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
            isCurrent = currentBssid != null && BSSID.equals(currentBssid, ignoreCase = true),
            isDfs = isDfsChannel(frequency, ch)
        )
    }

    /** 5 GHz 的 52–64、100–144 属 DFS 频段，需雷达检测；6 GHz 与 2.4 GHz 无此要求 */
    fun isDfsChannel(freq: Int, channel: Int?): Boolean {
        if (freq !in 4900..5925 || channel == null) return false
        return channel in 52..64 || channel in 100..144
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
                overlapScore = overlap + self * 0.35,
                isDfs = list.firstOrNull()?.isDfs ?: false,
                selfScore = self
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
     * 推荐信道：按频段分别给出相对压力最低的信道，并说明依据。
     * - 2.4 GHz 只在 1/6/11 三个互不重叠信道里选；
     * - 5/6 GHz 在扫描到的信道里选，但对 DFS 信道加惩罚（雷达检测可能导致被迫切换），
     *   同等压力下优先非 DFS；无法获知当地法规与终端兼容性，故只作提示不作保证。
     * 当前信道压力已在最优的 10% 内时建议保持，避免无谓切换。
     * 返回：推荐信道 / 理由 / 备选信道（压力次低的两个）。
     */
    private fun recommendWithReason(
        channels: List<ChannelStat>,
        current: Int?
    ): Triple<Int?, String?, List<Int>> {
        if (channels.isEmpty()) return Triple(null, "样本不足，无法给出推荐", emptyList())
        val currentStat = channels.firstOrNull { it.channel == current }
        val band = currentStat?.band ?: channels.first().band

        val pool = if (band == "2.4 GHz") {
            channels.filter { it.band == "2.4 GHz" && it.channel in listOf(1, 6, 11) }
                .ifEmpty { channels.filter { it.band == "2.4 GHz" } }
        } else {
            channels.filter { it.band == band }
        }.ifEmpty { channels }

        // DFS 惩罚：相对压力 ×1.15，仅在 5 GHz 生效
        fun effectiveScore(s: ChannelStat) =
            if (s.isDfs && s.band == "5 GHz") s.overlapScore * 1.15 else s.overlapScore
        val ranked = pool.sortedBy { effectiveScore(it) }
        val best = ranked.firstOrNull() ?: return Triple(null, "样本不足", emptyList())

        val alternatives = ranked.drop(1).take(2).map { it.channel }
        val currentEffective = currentStat?.let { effectiveScore(it) }
        val reason = buildString {
            append("依据本轮扫描到的 ").append(best.count).append(" 个 AP 与邻近信道重叠压力，")
            append("CH ").append(best.channel).append(" 的相对压力最低")
            if (best.isDfs) append("；该信道属 DFS，路由器可能触发雷达检测并自动换信道")
            if (currentEffective != null && currentEffective <= effectiveScore(best) * 1.1) {
                append("。当前信道已在最优范围内，无需切换")
            }
            append("。这是相对比较，不是实测空口占用率；当地法规与终端兼容性未纳入判断")
        }
        val recommended = if (currentEffective != null && currentEffective <= effectiveScore(best) * 1.1)
            current else best.channel
        return Triple(recommended, reason, alternatives)
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
