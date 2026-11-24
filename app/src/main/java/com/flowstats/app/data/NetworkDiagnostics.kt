package com.flowstats.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.math.abs
import kotlin.math.sqrt

data class PingReport(
    val host: String = "",
    val sent: Int = 0,
    val received: Int = 0,
    val lossPercent: Int = 0,
    val rttMin: Double = 0.0,
    val rttAvg: Double = 0.0,
    val rttMax: Double = 0.0,
    /** 相邻两次 RTT 差值的平均值，反映网络稳定性 */
    val jitter: Double = 0.0,
    val samples: List<Double> = emptyList(),
    val error: String? = null
) {
    val ok: Boolean get() = error == null && received > 0
}

object NetworkDiagnostics {

    private const val DEFAULT_HOST = "223.5.5.5"

    // 国内网络下单一接口经常不可达，按顺序兜底
    private val IPV4_ENDPOINTS = listOf(
        "https://myip.ipip.net",
        "https://api.ipify.org",
        "https://ipv4.icanhazip.com",
        "https://ipinfo.io/ip",
        "https://ifconfig.me/ip"
    )
    private val IPV6_ENDPOINTS = listOf(
        "https://api6.ipify.org",
        "https://ipv6.icanhazip.com"
    )
    private val ipv4Regex = Regex(
        """\b(?:(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\b"""
    )
    private val ipv6Regex = Regex("""\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\b""")

    /**
     * 用系统自带 ping 二进制做真实 ICMP 测量。
     * 非 root 下普通 App 无法直接发 ICMP，ping 命令是唯一可用途径。
     */
    fun ping(host: String = DEFAULT_HOST, count: Int = 8): PingReport {
        val target = host.ifBlank { DEFAULT_HOST }
        return pingInternal(target, count, interval = "0.3", timeoutSec = 2)
    }

    /**
     * 1 秒轮询专用：2 个包、间隔 0.2 秒、超时 1 秒，整轮约 0.5 秒内结束，
     * 保证能在 1 秒周期内完成且不与前一轮重叠。包数少意味着丢包率只有 0%/50% 两档，
     * 这是为「每秒刷新」做的精度取舍，不伪造任何数值。
     */
    fun pingQuick(host: String = DEFAULT_HOST): PingReport =
        pingInternal(host.ifBlank { DEFAULT_HOST }, count = 2, interval = "0.2", timeoutSec = 1)

    private fun pingInternal(target: String, count: Int, interval: String, timeoutSec: Int): PingReport {
        val samples = mutableListOf<Double>()
        var transmitted = 0
        var received = 0
        var error: String? = null

        try {
            val process = ProcessBuilder(
                "/system/bin/ping", "-c", count.toString(), "-i", interval, "-W", timeoutSec.toString(), target
            ).redirectErrorStream(true).start()

            val timeRegex = Regex("""time[=<]([\d.]+)\s*ms""")
            process.inputStream.bufferedReader().forEachLine { line ->
                timeRegex.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { samples += it }
                if (line.contains("packets transmitted")) {
                    Regex("""(\d+)\s+packets transmitted,\s*(\d+)\s+received""")
                        .find(line)?.let {
                            transmitted = it.groupValues[1].toIntOrNull() ?: 0
                            received = it.groupValues[2].toIntOrNull() ?: 0
                        }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            error = "无法执行 ping：${e.message ?: "未知错误"}"
        }

        if (transmitted == 0) transmitted = count
        if (received == 0 && samples.isNotEmpty()) received = samples.size
        if (samples.isEmpty() && error == null) error = "目标无响应（可能被防火墙拦截 ICMP）"

        val avg = if (samples.isEmpty()) 0.0 else samples.average()
        val jitter = if (samples.size < 2) 0.0 else
            samples.zipWithNext { a, b -> abs(b - a) }.average()

        return PingReport(
            host = target,
            sent = transmitted,
            received = received,
            lossPercent = if (transmitted == 0) 100
            else ((transmitted - received) * 100 / transmitted),
            rttMin = samples.minOrNull() ?: 0.0,
            rttAvg = avg,
            rttMax = samples.maxOrNull() ?: 0.0,
            jitter = jitter,
            samples = samples,
            error = error
        )
    }

    /**
     * 直接向 DNS 服务器发一个 A 查询并计时，绕开系统 DNS 缓存，
     * 得到的是真实 DNS 往返延迟。
     */
    fun dnsLatency(servers: List<String>, name: String = "www.baidu.com"): Pair<String, Double>? {
        val candidates = servers.mapNotNull { it.substringBefore('%') }
            .filter { it.isNotBlank() && !it.contains(':') }
        for (server in candidates) {
            val ms = queryOnce(server, name) ?: continue
            return server to ms.toDouble()
        }
        return null
    }

    private fun queryOnce(server: String, name: String): Long? = runCatching {
        val id = (System.nanoTime() and 0xFFFF).toInt()
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.writeShort(id)
        out.writeShort(0x0100)   // 标准查询 + 递归
        out.writeShort(1)        // QDCOUNT
        out.writeShort(0)
        out.writeShort(0)
        out.writeShort(0)
        name.split('.').forEach { part ->
            out.writeByte(part.length)
            out.writeBytes(part)
        }
        out.writeByte(0)
        out.writeShort(1)        // A
        out.writeShort(1)        // IN
        val payload = bytes.toByteArray()

        DatagramSocket().use { socket ->
            socket.soTimeout = 2000
            val address = InetAddress.getByName(server)
            socket.send(DatagramPacket(payload, payload.size, address, 53))
            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            val start = System.nanoTime()
            socket.receive(response)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (response.length >= 12) elapsed else null
        }
    }.getOrNull()

    /**
     * 公网 IP 走外部 HTTP 接口。
     * 之前只用了 api.ipify.org，国内网络下经常连不上，界面就一直停在「获取中」。
     * 现在按顺序试多个接口，全部失败时明确返回 error，由界面显示「获取失败 · 点击重试」，
     * 不会假装还在加载。
     */
    suspend fun publicIpInfo(): PublicIpInfo = coroutineScope {
        // 并发探测：单个接口卡住不会拖慢整体，最坏也只等一个超时
        val v4 = async(Dispatchers.IO) { raceFirst(IPV4_ENDPOINTS, ipv4Regex) }
        val v6 = async(Dispatchers.IO) { raceFirst(IPV6_ENDPOINTS, ipv6Regex) }
        val ipv4 = v4.await()
        val ipv6 = v6.await()
        val error = if (ipv4 == null && ipv6 == null) "网络接口无响应或被拦截" else null
        PublicIpInfo(ipv4 = ipv4, ipv6 = ipv6, error = error)
    }

    private suspend fun raceFirst(endpoints: List<String>, regex: Regex): String? = coroutineScope {
        val jobs = endpoints.map { url ->
            async(Dispatchers.IO) {
                runCatching { httpGet(url)?.let { regex.find(it)?.value } }.getOrNull()
            }
        }
        val values = jobs.awaitAll()
        values.firstOrNull { it != null }
    }

    private fun httpGet(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 2500
            requestMethod = "GET"
            setRequestProperty("User-Agent", "NetPulsePro/3.1")
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText().trim() }.take(256)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * DNS 解析对比：分别向系统 DNS 与公共 DNS 发同一条 A 查询，
     * 用来判断是「本地 DNS 慢」还是「全网都慢」。参考 NSLookup 的做法。
     */
    suspend fun dnsCompare(systemServers: List<String>, name: String = "www.baidu.com"): List<DnsProbe> {
        val system = systemServers.firstOrNull { it.isNotBlank() && !it.contains(':') }
        val targets = buildList {
            if (system != null) add(system to "系统 DNS")
            add("223.5.5.5" to "阿里 DNS")
            add("119.29.29.29" to "腾讯 DNS")
        }
        return coroutineScope {
            val jobs = targets.map { (server, label) ->
                async(Dispatchers.IO) {
                    DnsProbe(server = server, label = label, ms = queryOnce(server, name)?.toDouble())
                }
            }
            jobs.awaitAll()
        }
    }

    /** 稳定性评分：综合丢包、抖动、平均延迟，0~100 */
    fun stabilityScore(report: PingReport): Int {
        if (!report.ok) return 0
        var score = 100.0
        score -= report.lossPercent * 4.0
        score -= (report.jitter * 1.2).coerceAtMost(35.0)
        score -= ((report.rttAvg - 30).coerceAtLeast(0.0) / 3.0).coerceAtMost(30.0)
        return score.coerceIn(0.0, 100.0).toInt()
    }

    /** 抖动标准差，用于绘图时的离散度参考 */
    fun jitterStdDev(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        val mean = samples.average()
        return sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
    }
}

/** 公网出口 IP；拿不到时 error 非空，界面据此显示失败态而不是一直转圈 */
data class PublicIpInfo(
    val ipv4: String? = null,
    val ipv6: String? = null,
    val error: String? = null
) {
    val ok: Boolean get() = ipv4 != null || ipv6 != null
}

/** 单个 DNS 服务器的解析往返时间，ms 为 null 表示无响应 */
data class DnsProbe(
    val server: String,
    val label: String,
    val ms: Double?
)

data class MemoryStat(
    val totalBytes: Long = 0,
    val availBytes: Long = 0,
    val usedPercent: Int = 0,
    val lowMemory: Boolean = false
) {
    val available: Boolean get() = totalBytes > 0
}

object DeviceStats {
    /**
     * 整机 CPU 使用率在 Android 8 之后没有公开 API（/proc/stat 对第三方应用已关闭），
     * 这里只提供真实可读的内存占用，不去伪造 CPU 百分比。
     */
    fun memory(context: Context): MemoryStat {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
            ?: return MemoryStat()
        val info = android.app.ActivityManager.MemoryInfo()
        return runCatching {
            am.getMemoryInfo(info)
            val total = info.totalMem
            val avail = info.availMem
            MemoryStat(
                totalBytes = total,
                availBytes = avail,
                usedPercent = if (total <= 0) 0
                else (((total - avail) * 100) / total).toInt().coerceIn(0, 100),
                lowMemory = info.lowMemory
            )
        }.getOrDefault(MemoryStat())
    }
}
