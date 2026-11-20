package com.flowstats.app.data

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一台局域网设备的“发现方式”。不同方式可信度不同，必须如实展示，
 * 不能把「历史记录」当成当前在线，也不能把「端口响应」当成设备类型判断依据。
 */
enum class DiscoverySource(val label: String, val confidence: String) {
    SELF("本机", "确定"),
    GATEWAY("网关配置", "确定"),
    ICMP("ICMP 响应", "高"),
    ARP("ARP 表", "中"),
    TCP("TCP 端口响应", "中"),
    HISTORY("历史记录", "低")
}

/** 单次扫描的完成状态：未完成时不把设备数当作最终结果 */
enum class ScanStatus(val label: String) {
    RUNNING("扫描中"),
    COMPLETE("扫描完成"),
    STOPPED("扫描中断"),
    FAILED("扫描失败")
}

/** 局域网设备：所有字段来自 ICMP 探测、/proc/net/arp、TCP 连接或本地历史，不猜 MAC 和厂商 */
data class LanDevice(
    val ip: String,
    val mac: String?,
    val vendor: String?,
    val hostname: String?,
    val isGateway: Boolean,
    val isSelf: Boolean,
    val isNew: Boolean,
    val firstSeen: Long,
    val lastSeen: Long,
    val openPorts: List<Int>,
    /** 本轮发现方式（历史记录不计入当前响应） */
    val sources: List<DiscoverySource>,
    /** 是否存在本轮真实响应（ICMP/ARP/TCP/本机/网关），false 表示仅历史命中 */
    val respondedNow: Boolean,
    /** 设备类型：仅依据主机名与厂商的粗略推测，可能是「未知终端」 */
    val deviceType: String,
    /** ICMP 响应延迟（实测 RTT），仅 ping 命中且有 time= 输出时才有值 */
    val responseMs: Double? = null,
    /** 端口探测耗时（本轮实测） */
    val portProbeMs: Long? = null
) {
    val primarySource: DiscoverySource
        get() = sources.firstOrNull() ?: DiscoverySource.HISTORY
}

data class LanReport(
    val time: Long = 0L,
    val gatewayV4: String? = null,
    val gatewayV6: String? = null,
    val localIp: String? = null,
    val subnet: String? = null,
    val mask: String? = null,
    val scanRange: String? = null,
    val scanned: Int = 0,
    val total: Int = 0,
    val devices: List<LanDevice> = emptyList(),
    /** 去重的唯一设备数（同一设备被多种方式发现只计一次），不含本机 */
    val uniqueDeviceCount: Int = 0,
    /** 各发现方式的命中次数：可能重叠，之和不等于唯一设备数 */
    val sourceCounts: Map<DiscoverySource, Int> = emptyMap(),
    val scanStatus: ScanStatus = ScanStatus.COMPLETE,
    val message: String? = null,
    val error: String? = null,
    val stopped: Boolean = false
) {
    val progressPercent: Int
        get() = if (total > 0) (scanned * 100 / total).coerceIn(0, 100) else 0
}

/**
 * 局域网设备扫描。
 *
 * 真实来源：LinkProperties 得到 IPv4/IPv6 网关与子网；ICMP ping 探测在线；
 * /proc/net/arp 取 MAC 与二层可见设备；TCP connect 探测常见端口；反查主机名。
 * Android 对新版 MAC 有隐私限制，很多设备只能看到 00:00:00:00:00:00，
 * 此时一律显示「不可见」，不编造。ICMP 不响应不代表离线，界面按发现方式分别标注。
 */
object LanScanner {

    private const val PREF = "lan_devices"
    private const val KEY = "seen"
    private const val COMMON_PORTS = "22,80,443,8080"
    private const val PROGRESS_STEP = 8

    private val OUI = mapOf(
        "FCFBFB" to "Apple", "A4D18C" to "Apple", "3C22FB" to "Apple", "B827EB" to "树莓派",
        "00259C" to "TP-LINK", "E8DCC6" to "TP-LINK", "C861A8" to "华为", "DC7E08" to "华为",
        "AC83C9" to "小米", "78C627" to "小米", "8CD499" to "小米", "4C49E0" to "三星",
        "D8C693" to "三星", "001B21" to "Intel", "A4BB6D" to "联想", "F0D1F2" to "大疆",
        "B09575" to "Espressif", "24A1E7" to "华为", "00E0EC" to "中兴"
    )

    private data class Seen(val first: Long, val last: Long, val ports: List<Int>)

    private fun readSeen(context: Context): MutableMap<String, Seen> {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableMapOf()
        return runCatching {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, Seen>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ports = o.optString("p", "").split(",").mapNotNull { it.toIntOrNull() }
                map[o.optString("ip")] = Seen(o.optLong("f"), o.optLong("l"), ports)
            }
            map
        }.getOrElse { mutableMapOf() }
    }

    private fun writeSeen(context: Context, map: Map<String, Seen>) {
        val arr = JSONArray()
        map.forEach { (ip, s) ->
            val o = JSONObject()
            o.put("ip", ip); o.put("f", s.first); o.put("l", s.last)
            o.put("p", s.ports.joinToString(","))
            arr.put(o)
        }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** 链路信息：IPv4/IPv6 默认网关必须分开，IPv6 常为 fe80:: 链路本地地址 */
    private data class LinkInfo(
        val gatewayV4: String?,
        val gatewayV6: String?,
        val localIp: String?,
        val prefix: Int?
    )

    private fun linkInfo(context: Context): LinkInfo {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return LinkInfo(null, null, null, null)
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return LinkInfo(null, null, null, null)
        val routes = lp.routes.filter { it.isDefaultRoute }
        val gw4 = routes.firstOrNull { it.gateway is Inet4Address }?.gateway?.hostAddress
        val gw6 = routes.firstOrNull { it.gateway !is Inet4Address }?.gateway?.hostAddress
        val addr = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
        return LinkInfo(gw4, gw6, addr?.address?.hostAddress, addr?.prefixLength)
    }

    private fun prefixToMask(prefix: Int?): String? {
        if (prefix == null || prefix !in 1..32) return null
        val bits = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        return listOf(bits shr 24, (bits shr 16) and 0xff, (bits shr 8) and 0xff, bits and 0xff)
            .joinToString(".") { (it and 0xff).toString() }
    }

    /**
     * 扫描本机 /24 网段。
     * @param onProgress 进度回调 (已探测, 总数, 当前已发现响应设备数)，每 8 个地址回报一次
     * @param shouldStop 返回 true 时立即停止后续探测（用于「停止扫描」按钮）
     */
    suspend fun scan(
        context: Context,
        onProgress: (Int, Int, Int) -> Unit = { _, _, _ -> },
        shouldStop: () -> Boolean = { false }
    ): LanReport = withContext(Dispatchers.IO) {
        val (gw4, gw6, localIp, prefix) = linkInfo(context)
        if (localIp == null) {
            return@withContext LanReport(error = "未获取到 IPv4 局域网地址，请连接 Wi-Fi 后重试")
        }
        val base = localIp.substringBeforeLast('.')
        val mask = prefixToMask(prefix)
        val subnet = if (prefix != null && prefix in 1..32) "$localIp/$prefix" else "$base.0/24"
        val seen = readSeen(context)
        val now = System.currentTimeMillis()

        // /proc/net/arp：只取 Flags=0x2（完成条目）且 MAC 合法的行
        val arp: Map<String, String> = runCatching {
            java.io.File("/proc/net/arp").readLines().drop(1).mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"))
                if (p.size >= 4 && p[2].equals("0x2", ignoreCase = true) &&
                    p[3].matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))
                ) p[0] to p[3] else null
            }.toMap()
        }.getOrElse { emptyMap() }

        val targets = (1..254).map { "$base.$it" }.filter { it != localIp }
        val sources = mutableMapOf<String, MutableSet<DiscoverySource>>()
        fun addSource(ip: String, s: DiscoverySource) {
            sources.getOrPut(ip) { mutableSetOf() }.add(s)
        }
        addSource(localIp, DiscoverySource.SELF)
        gw4?.let { if (it != localIp) addSource(it, DiscoverySource.GATEWAY) }
        arp.keys.filter { it.startsWith("$base.") }.forEach { addSource(it, DiscoverySource.ARP) }

        val semaphore = Semaphore(24)
        var scannedCount = 0
        val alive = coroutineScope {
            targets.map { ip ->
                async(Dispatchers.IO) {
                    if (shouldStop()) return@async null
                    semaphore.acquire()
                    try {
                        val probe = if (shouldStop()) null else pingOnce(ip)
                        val n = ++scannedCount
                        if (n % PROGRESS_STEP == 0 || n == targets.size) {
                            val found = sources.keys.count { it != localIp }
                            onProgress(n, targets.size, found)
                        }
                        // 打包成 (IP, RTT)，只保留 ping 命中的
                        if (probe?.first == true) ip to probe.second else null
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        // 实测 RTT：ping 命中且输出里解析到 time= 时才有值，否则留空
        val rttByIp: Map<String, Double?> = alive.associate { (ip, rtt) -> ip to rtt }
        alive.forEach { (ip, _) -> addSource(ip, DiscoverySource.ICMP) }

        val stopped = shouldStop()
        // 所有当前有来源的 IP（含本机、网关、ARP、ICMP），端口仅在设备数不多时探测并缓存结果
        portCache.clear()
        val portCost = mutableMapOf<String, Long>()
        val currentIps = sources.keys.toList()
        if (currentIps.size <= 40 && !stopped) {
            currentIps.forEach { ip ->
                val t0 = System.currentTimeMillis()
                val ports = probePorts(ip)
                portCost[ip] = System.currentTimeMillis() - t0
                portCache[ip] = ports
                if (ports.isNotEmpty()) addSource(ip, DiscoverySource.TCP)
            }
        }
        // 超过 40 台跳过端口探测时，明确记录为未探测，不伪造耗时
        currentIps.forEach { if (it !in portCost) portCost[it] = -1L }
        // 历史记录中的设备也展示，但明确标为「历史记录 / 低可信」，不计入本轮响应
        seen.keys.filter { it.startsWith("$base.") && it !in sources }
            .forEach { addSource(it, DiscoverySource.HISTORY) }

        val devices = sources.map { (ip, src) ->
            val mac = arp[ip]
            val vendor = mac?.let { OUI[it.replace(":", "").uppercase().take(6)] }
            val responded = DiscoverySource.values().any { it != DiscoverySource.HISTORY && it in src }
            val hostname = if (responded) runCatching {
                kotlinx.coroutines.withTimeout(900) {
                    InetAddress.getByName(ip).hostName.takeIf { it != ip && it.isNotBlank() }
                }
            }.getOrNull() else null
            val ports = if (responded) probePortsCached(ip) else emptyList()
            val ordered = orderSources(src)
            val old = seen[ip]
            val isNew = old == null && responded
            val rec = Seen(old?.first ?: now, if (responded) now else (old?.last ?: now), ports)
            if (responded) seen[ip] = rec
            LanDevice(
                ip = ip,
                mac = mac,
                vendor = vendor,
                hostname = hostname,
                isGateway = ip == gw4,
                isSelf = ip == localIp,
                isNew = isNew,
                firstSeen = rec.first,
                lastSeen = rec.last,
                openPorts = ports,
                sources = ordered,
                respondedNow = responded,
                deviceType = guessType(hostname, vendor, ports),
                responseMs = rttByIp[ip],
                portProbeMs = portCost[ip]?.takeIf { it >= 0 }
            )
        }.sortedWith(compareByDescending<LanDevice> { it.isGateway }
            .thenByDescending { it.respondedNow }
            .thenBy { ipOrder(it.ip) })

        writeSeen(context, seen)
        // 发现方式命中次数：同一设备可被多种方式发现，此处按方式累加，可能重叠
        val counts = sources.values.flatMap { it.toList() }
            .filter { it != DiscoverySource.HISTORY }
            .groupingBy { it }.eachCount()
        // 去重后的唯一设备数：一台设备只计一次，不含本机
        val respondedCount = devices.count { it.respondedNow && !it.isSelf }
        // 本函数返回即代表本轮已结束：未手动停止就是完成（ begun 扫描中由 UI 的 scanning 状态表达）。
        // scannedCount < total 只说明部分地址未响应，不是"仍在扫描"。
        val status = if (stopped) ScanStatus.STOPPED else ScanStatus.COMPLETE
        val parts = mutableListOf<String>()
        DiscoverySource.values().filter { it != DiscoverySource.HISTORY && it != DiscoverySource.SELF }
            .forEach { s -> counts[s]?.let { parts.add("${s.label} $it 台次") } }
        val statusText = when (status) {
            ScanStatus.COMPLETE -> "扫描完成"
            ScanStatus.STOPPED -> "扫描已中断"
            ScanStatus.RUNNING -> "扫描尚未完成"
            ScanStatus.FAILED -> "扫描失败"
        }
        LanReport(
            time = now,
            gatewayV4 = gw4,
            gatewayV6 = gw6,
            localIp = localIp,
            subnet = subnet,
            mask = mask,
            scanRange = "$base.1–254",
            scanned = scannedCount,
            total = targets.size,
            devices = devices,
            uniqueDeviceCount = respondedCount,
            sourceCounts = counts,
            scanStatus = status,
            message = "$statusText · 探测 $scannedCount/${targets.size} 个地址" +
                "（${(scannedCount * 100 / targets.size.coerceAtLeast(1))}%）· " +
                "去重后响应设备 $respondedCount 台。各方式命中：" +
                (if (parts.isNotEmpty()) parts.joinToString(" · ") else "无") +
                "。同一设备可能被多种方式命中，台次不累加；ICMP 不响应不等于离线。",
            stopped = stopped
        )
    }

    /** 端口结果缓存：上面已探测过一次，避免重复连接 */
    private val portCache = mutableMapOf<String, List<Int>>()
    private fun probePortsCached(ip: String): List<Int> =
        portCache.getOrPut(ip) { probePorts(ip) }

    private fun orderSources(src: Set<DiscoverySource>): List<DiscoverySource> =
        listOf(DiscoverySource.SELF, DiscoverySource.GATEWAY, DiscoverySource.ICMP,
            DiscoverySource.TCP, DiscoverySource.ARP, DiscoverySource.HISTORY)
            .filter { it in src }

    /** 设备类型只作粗略提示，绝不因开放端口就断言设备种类 */
    private fun guessType(hostname: String?, vendor: String?, ports: List<Int>): String {
        val h = hostname.orEmpty().lowercase()
        val v = vendor.orEmpty().lowercase()
        return when {
            h.contains("android") || h.contains("iphone") || h.contains("phone") -> "手机（依主机名推测）"
            h.contains("pc") || h.contains("desktop") || h.contains("laptop") || h.contains("macbook") -> "电脑（依主机名推测）"
            v == "apple" -> "Apple 设备（依 MAC 厂商推测）"
            v.isNotBlank() -> "$vendor 设备（依 MAC 厂商推测）"
            else -> "未知终端"
        }
    }

    private fun ipOrder(ip: String): Int = ip.substringAfterLast('.').toIntOrNull() ?: 0

    /** 返回 (是否在线, 实测 RTT ms)；RTT 只在 ping 输出含 time= 时才有值 */
    private fun pingOnce(ip: String): Pair<Boolean, Double?> = runCatching {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", ip)
            .redirectErrorStream(true).start()
        val output = p.inputStream.bufferedReader().readText()
        val ok = p.waitFor(1800, TimeUnit.MILLISECONDS)
        if (!ok) {
            p.destroyForcibly()
            false to null
        } else if (p.exitValue() == 0) {
            val rtt = Regex("""time[=<]([\d.]+)\s*ms""")
                .find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            true to rtt
        } else false to null
    }.getOrDefault(false to null)

    private fun probePorts(ip: String): List<Int> {
        val ports = COMMON_PORTS.split(",").mapNotNull { it.toIntOrNull() }
        return ports.filter { port ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 220)
                    true
                }
            }.getOrDefault(false)
        }
    }

    fun clearHistory(context: Context) {
        portCache.clear()
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
