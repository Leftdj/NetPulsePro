package com.flowstats.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 局域网设备：所有字段来自 ICMP 探测、/proc/net/arp、TCP 连接或本地历史，不猜 MAC 和厂商 */
data class LanDevice(
    val ip: String,
    val mac: String?,
    val vendor: String?,
    val hostname: String?,
    val isGateway: Boolean,
    val isNew: Boolean,
    val firstSeen: Long,
    val lastSeen: Long,
    val openPorts: List<Int>
)

data class LanReport(
    val time: Long = 0L,
    val gateway: String? = null,
    val localIp: String? = null,
    val subnet: String? = null,
    val devices: List<LanDevice> = emptyList(),
    val message: String? = null,
    val error: String? = null
)

/**
 * 局域网设备扫描。
 *
 * 真实来源：LinkProperties 得到网关与子网；ICMP  ping 探测在线；/proc/net/arp 取 MAC；
 * TCP connect 探测常见端口；反查主机名。Android 对新版 MAC 有隐私限制，
 * 很多设备只能看到 00:00:00:00:00:00，此时一律显示「不可见」，不编造。
 */
object LanScanner {

    private const val PREF = "lan_devices"
    private const val KEY = "seen"
    private const val COMMON_PORTS = "22,80,443,8080"

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

    private fun linkInfo(context: Context): Triple<String?, String?, Int?> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Triple(null, null, null)
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return Triple(null, null, null)
        val gateway = lp.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        val addr = lp.linkAddresses.firstOrNull { it.address is Inet4Address }
        val local = addr?.address?.hostAddress
        return Triple(gateway, local, addr?.prefixLength)
    }

    /** 扫描本机的 /24 网段（若掩码更窄只取本地 /24，避免扫出成千上万个地址） */
    suspend fun scan(context: Context): LanReport = withContext(Dispatchers.IO) {
        val (gateway, localIp, prefix) = linkInfo(context)
        if (localIp == null) {
            return@withContext LanReport(error = "未获取到 IPv4 局域网地址，请连接 Wi-Fi 后重试")
        }
        val base = localIp.substringBeforeLast('.')
        val subnet = if (prefix != null && prefix in 1..31) "$localIp/$prefix" else "$base.0/24"
        val seen = readSeen(context)
        val now = System.currentTimeMillis()

        val arp = runCatching {
            java.io.File("/proc/net/arp").readLines().drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) parts[0] to parts[3] else null
            }.toMap()
        }.getOrElse { emptyMap() }

        val targets = (1..254).map { "$base.$it" }.filter { it != localIp }
        val semaphore = kotlinx.coroutines.sync.Semaphore(24)
        val alive = coroutineScope {
            targets.map { ip ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        if (pingOnce(ip)) ip else null
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val all = (listOfNotNull(localIp, gateway) + alive).distinct()
        val devices = all.map { ip ->
            val mac = arp[ip]?.takeIf { it.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")) && it != "00:00:00:00:00:00" }
            val vendor = mac?.let { OUI[it.replace(":", "").uppercase().take(6)] }
            val hostname = runCatching {
                kotlinx.coroutines.withTimeout(900) {
                    InetAddress.getByName(ip).hostName.takeIf { it != ip && it.isNotBlank() }
                }
            }.getOrNull()
            val ports = if (all.size <= 40) probePorts(ip) else emptyList()
            val old = seen[ip]
            val isNew = old == null
            val rec = Seen(old?.first ?: now, now, ports)
            seen[ip] = rec
            LanDevice(
                ip = ip,
                mac = mac,
                vendor = vendor,
                hostname = hostname,
                isGateway = ip == gateway,
                isNew = isNew,
                firstSeen = rec.first,
                lastSeen = rec.last,
                openPorts = ports
            )
        }.sortedWith(compareByDescending<LanDevice> { it.isGateway }.thenBy { ipOrder(it.ip) })

        writeSeen(context, seen)
        LanReport(
            time = now,
            gateway = gateway,
            localIp = localIp,
            subnet = subnet,
            devices = devices,
            message = "ICMP 探测 ${targets.size} 个地址，在线 ${devices.size} 台；" +
                "MAC/厂商仅在系统 ARP 表可见且有内置 OUI 记录时显示，端口仅探测 $COMMON_PORTS"
        )
    }

    private fun ipOrder(ip: String): Int =
        ip.substringAfterLast('.').toIntOrNull() ?: 0

    private fun pingOnce(ip: String): Boolean = runCatching {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", ip)
            .redirectErrorStream(true).start()
        val ok = p.waitFor(1800, TimeUnit.MILLISECONDS)
        if (ok) p.exitValue() == 0 else {
            p.destroyForcibly()
            false
        }
    }.getOrDefault(false)

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
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
