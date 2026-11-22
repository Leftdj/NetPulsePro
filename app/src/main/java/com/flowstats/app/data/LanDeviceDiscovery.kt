package com.flowstats.app.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * 主动设备身份发现：mDNS / SSDP(UPnP) / NetBIOS。
 *
 * 全部为真实网络探测：设备自己广播/应答的名字、厂商、型号才写入结果；
 * 探测不到、超时、被系统或路由器过滤时返回空，界面显示「未知终端」，绝不伪造。
 * 三种方式彼此独立，任一失败不影响其它。
 */
data class DiscoveredIdentity(
    val name: String?,
    val vendor: String?,
    val model: String?,
    val source: String
)

object LanDeviceDiscovery {

    /** 汇总三种发现结果，按 IP 合并；同名多次取信息最全的一条 */
    suspend fun discover(context: Context, localIp: String?, timeoutMs: Long = 4500L): Map<String, DiscoveredIdentity> =
        withContext(Dispatchers.IO) {
            val result = ConcurrentHashMap<String, DiscoveredIdentity>()
            kotlinx.coroutines.coroutineScope {
                val a = async {
                    runCatching { merge(result, discoverMdns(context, timeoutMs)) }
                }
                val b = async {
                    runCatching { merge(result, discoverSsdp(context, timeoutMs)) }
                }
                val c = async {
                    runCatching { merge(result, discoverNetbios(localIp, timeoutMs)) }
                }
                a.await(); b.await(); c.await()
            }
            result
        }

    private fun merge(
        target: ConcurrentHashMap<String, DiscoveredIdentity>,
        incoming: Map<String, DiscoveredIdentity>
    ) {
        incoming.forEach { (ip, id) ->
            val old = target[ip]
            val better = old == null || id.name != null && old.name == null ||
                (id.vendor != null && old.vendor == null) || (id.model != null && old.model == null)
            if (better) target[ip] = id
        }
    }

    private fun isLocalSubnet(ip: String, localIp: String?): Boolean {
        if (localIp == null) return true
        val base = localIp.substringBeforeLast('.')
        return ip.startsWith("$base.")
    }

    // ── mDNS ─────────────────────────────────────────────
    /** 用 NsdManager 发现局域网自报服务；服务实例名常含设备名，host 提供 IP */
    private suspend fun discoverMdns(context: Context, timeoutMs: Long): Map<String, DiscoveredIdentity> {
        val found = ConcurrentHashMap<String, DiscoveredIdentity>()
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return emptyMap()
        // 每个服务类型配独立 listener，避免 stopServiceDiscovery 的 type 不匹配问题
        data class Reg(val type: String, val listener: NsdManager.DiscoveryListener)
        val regs = mutableListOf<Reg>()
        val types = listOf(
            "_googlecast._tcp.", "_ipp._tcp.", "_hap._tcp.",
            "_ssh._tcp.", "_smb._tcp.", "_http._tcp."
        )
        types.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    runCatching {
                        nsd.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(s: NsdServiceInfo?, errorCode: Int) {}
                            override fun onServiceResolved(s: NsdServiceInfo?) {
                                val host = s?.host?.hostAddress ?: return
                                val name = cleanMdnsName(s.serviceName ?: "")
                                if (name.isNotBlank()) {
                                    found[host] = DiscoveredIdentity(name, null, null, "mDNS")
                                }
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
            regs.add(Reg(type, listener))
        }
        kotlinx.coroutines.delay(timeoutMs - 300)
        regs.forEach { runCatching { nsd.stopServiceDiscovery(it.listener) } }
        return found
    }

    private fun cleanMdnsName(raw: String): String = raw
        .replace(Regex("^_?[0-9A-Fa-f]{12,}"), "")
        .replace(Regex("[._]"), " ")
        .trim()
        .take(32)

    // ── SSDP / UPnP ───────────────────────────────────────
    /** 组播 M-SEARCH，设备应答 LOCATION，取其 XML 的 friendlyName / manufacturer / modelName */
    private suspend fun discoverSsdp(context: Context, timeoutMs: Long): Map<String, DiscoveredIdentity> =
        withContext(Dispatchers.IO) {
            val found = ConcurrentHashMap<String, DiscoveredIdentity>()
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            val lock = wm?.createMulticastLock("netpulse_ssdp")?.apply {
                setReferenceCounted(false); acquire()
            }
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { soTimeout = 700 }
                val request = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: ssdp:all\r\n\r\n"
                    ).toByteArray()
                val packet = DatagramPacket(request, request.size,
                    InetSocketAddress("239.255.255.250", 1900))
                repeat(2) { runCatching { socket.send(packet) } }

                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val buf = ByteArray(4096)
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                        val location = Regex("""LOCATION:\s*(\S+)""", RegexOption.IGNORE_CASE)
                            .find(text)?.groupValues?.getOrNull(1) ?: continue
                        val remoteIp = (resp.address as? InetAddress)?.hostAddress ?: continue
                        val identity = parseUpnpXml(location, remoteIp) ?: continue
                        found[remoteIp] = identity
                    } catch (_: java.net.SocketTimeoutException) {
                        // 继续等待应答
                    } catch (_: Exception) {
                        // 单个应答解析失败不影响整体
                    }
                }
            } finally {
                runCatching { socket?.close() }
                runCatching { if (lock?.isHeld == true) lock.release() }
            }
            found
        }

    private fun parseUpnpXml(location: String, ip: String): DiscoveredIdentity? = runCatching {
        val xml = URL(location).openConnection().apply {
            connectTimeout = 800; readTimeout = 800
        }.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText().take(64 * 1024) }
        fun tag(name: String) = Regex("<$name\\s*>(.*?)</$name>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val friendly = tag("friendlyName")
        val manufacturer = tag("manufacturer")
        val model = tag("modelName")
        if (friendly == null && manufacturer == null && model == null) null
        else DiscoveredIdentity(friendly, manufacturer, model, "UPnP")
    }.getOrNull()

    // ── NetBIOS ───────────────────────────────────────────
    /** UDP 137 广播 NBNS 名字查询，解析应答中的机器名（Windows / 群晖等） */
    private suspend fun discoverNetbios(localIp: String?, timeoutMs: Long): Map<String, DiscoveredIdentity> =
        withContext(Dispatchers.IO) {
            if (localIp == null) return@withContext emptyMap()
            val found = ConcurrentHashMap<String, DiscoveredIdentity>()
            val base = localIp.substringBeforeLast('.')
            val broadcast = "$base.255"
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 600
                socket.broadcast = true
                socket.reuseAddress = true
                // NBNS 名字服务查询：事务 ID + 标志 + 问题数=1 + "*<00>" 编码 + 类型/类
                val query = buildNbnsQuery()
                val packet = DatagramPacket(query, query.size,
                    InetSocketAddress(broadcast, 137))
                repeat(2) { runCatching { socket.send(packet) } }

                val deadline = System.currentTimeMillis() + (timeoutMs - 400)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val buf = ByteArray(1024)
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val ip = (resp.address as? InetAddress)?.hostAddress ?: continue
                        if (!isLocalSubnet(ip, localIp)) continue
                        val name = parseNbnsAnswer(resp.data, resp.length) ?: continue
                        if (name.isNotBlank()) {
                            found[ip] = DiscoveredIdentity(name, null, null, "NetBIOS")
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                    } catch (_: Exception) {
                    }
                }
            } finally {
                runCatching { socket?.close() }
            }
            found
        }

    /** 构造 NBNS "*<00>" 名字查询包 */
    private fun buildNbnsQuery(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xff) }
        u16(0x1234)          // 事务 ID
        u16(0x0100)          // 标准查询、递归期望
        u16(1)               // QDCOUNT
        u16(0); u16(0); u16(0) // AN/NS/AR
        // 编码名字 "*" + 0x00 后缀：第一字节长度，字符大写，0x00 结束
        val name = "*"
        out.write(name.length + 1)
        name.uppercase().forEach { out.write(it.code) }
        out.write(0)
        out.write(0x00); out.write(0x20) // 后缀 0x00（Workstation），名字类型
        u16(1)               // QTYPE NB
        u16(1)               // QCLASS IN
        return out.toByteArray()
    }

    /** 从 NBNS 应答的答案段解析第一个 16 字节编码名字 */
    private fun parseNbnsAnswer(data: ByteArray, length: Int): String? = runCatching {
        if (length < 56) return null
        val qd = ((data[4].toInt() and 0xff) shl 8) or (data[5].toInt() and 0xff)
        val an = ((data[6].toInt() and 0xff) shl 8) or (data[7].toInt() and 0xff)
        if (an < 1) return null
        // 跳过问题段（按标签长度游走）
        var pos = 12
        repeat(qd) {
            while (pos < length && (data[pos].toInt() and 0xff) != 0) {
                pos += (data[pos].toInt() and 0xff) + 1
            }
            pos += 1 + 4 // 结束 0 + QTYPE + QCLASS
        }
        if (pos + 2 > length) return null
        // 答案 NAME 为指针 0xC00C
        if ((data[pos].toInt() and 0xff) != 0xC0) return null
        val rdata = pos + 2 + 2 + 2 + 4 + 2 // 指针 + TYPE + CLASS + TTL + RDLENGTH
        if (rdata + 16 > length) return null
        val sb = StringBuilder()
        for (i in rdata until rdata + 16 step 2) {
            val c = (((data[i].toInt() and 0xff) shl 8) or (data[i + 1].toInt() and 0xff)).toChar()
            if (c == ' ' || c.code == 0) break
            sb.append(c)
        }
        sb.toString().trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}
