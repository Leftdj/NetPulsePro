package com.flowstats.app.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class CheckStatus { OK, WARN, FAIL, UNKNOWN }

data class CheckItem(val label: String, val status: CheckStatus, val detail: String)

data class TargetSpec(val label: String, val host: String, val builtIn: Boolean = false)

data class TargetResult(
    val label: String,
    val host: String,
    val rttAvg: Double?,
    val lossPct: Int?,
    val error: String?
) {
    val ok: Boolean get() = rttAvg != null && (lossPct ?: 100) < 50
}

/** 一键体检 + 多目标监控。所有结果来自真实测量；拿不到就标 UNKNOWN，不补齐。 */
object AdvancedChecks {

    private const val PREF = "net_targets"
    private const val KEY = "custom"

    fun defaultTargets(gateway: String?): List<TargetSpec> = buildList {
        if (!gateway.isNullOrBlank()) add(TargetSpec("路由器网关", gateway, true))
        add(TargetSpec("公共 DNS", "223.5.5.5", true))
        add(TargetSpec("国内网站", "www.baidu.com", true))
        add(TargetSpec("国外网站", "1.1.1.1", true))
    }

    fun loadCustom(context: Context): List<TargetSpec> {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                TargetSpec(o.optString("l"), o.optString("h"))
            }.filter { it.host.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    fun addCustom(context: Context, label: String, host: String): List<TargetSpec> {
        val list = (loadCustom(context) + TargetSpec(label.ifBlank { host }, host.trim())).distinctBy { it.host }.take(8)
        persist(context, list)
        return list
    }

    fun removeCustom(context: Context, host: String): List<TargetSpec> {
        val list = loadCustom(context).filterNot { it.host == host }
        persist(context, list)
        return list
    }

    private fun persist(context: Context, list: List<TargetSpec>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("l", it.label); o.put("h", it.host)
            arr.put(o)
        }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** 一键体检：复用已采集的真实指标，网关可达性单独 ping */
    suspend fun runChecks(
        context: Context,
        wifi: WifiDetail,
        ping: PingReport,
        dnsMs: Double?,
        publicIp: String?,
        publicIpv6: String?
    ): List<CheckItem> = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val connected = cm?.activeNetwork != null
        val gatewayReport = if (wifi.gateway.isNullOrBlank()) null
        else runCatching { NetworkDiagnostics.ping(wifi.gateway!!, count = 3) }.getOrNull()
        val gatewayIp = wifi.ipv4?.substringBefore('/')

        listOf(
            CheckItem(
                "网络连接",
                if (connected) CheckStatus.OK else CheckStatus.FAIL,
                if (connected) "系统存在活跃数据网络" else "未检测到活跃网络"
            ),
            CheckItem(
                "DNS 解析",
                when {
                    dnsMs == null -> CheckStatus.UNKNOWN
                    dnsMs < 100 -> CheckStatus.OK
                    else -> CheckStatus.WARN
                },
                dnsMs?.let { "当前 DNS 解析约 ${it.toInt()} ms" } ?: "未测量"
            ),
            CheckItem(
                "IPv4 地址",
                if (!gatewayIp.isNullOrBlank() || !publicIp.isNullOrBlank()) CheckStatus.OK else CheckStatus.UNKNOWN,
                gatewayIp?.let { "本机 $it" } ?: publicIp?.let { "公网 $it" } ?: "系统未上报"
            ),
            CheckItem(
                "公网 IPv6",
                if (publicIpv6.isNullOrBlank()) CheckStatus.WARN else CheckStatus.OK,
                if (publicIpv6.isNullOrBlank()) "未检测到公网 IPv6" else "已连通"
            ),
            CheckItem(
                "网关可达",
                when {
                    gatewayReport == null -> CheckStatus.UNKNOWN
                    gatewayReport.ok -> CheckStatus.OK
                    else -> CheckStatus.FAIL
                },
                gatewayReport?.let {
                    if (it.ok) "平均 ${it.rttAvg.toInt()} ms · 丢包 ${it.lossPercent}%"
                    else "ICMP 不通：${it.error ?: "无响应"}"
                } ?: "系统未上报网关"
            ),
            CheckItem(
                "链路延迟",
                when {
                    !ping.ok -> CheckStatus.UNKNOWN
                    ping.rttAvg < 150 -> CheckStatus.OK
                    else -> CheckStatus.WARN
                },
                if (ping.ok) "平均 ${ping.rttAvg.toInt()} ms" else "未测量"
            ),
            CheckItem(
                "链路丢包",
                when {
                    !ping.ok -> CheckStatus.UNKNOWN
                    ping.lossPercent == 0 -> CheckStatus.OK
                    ping.lossPercent < 5 -> CheckStatus.WARN
                    else -> CheckStatus.FAIL
                },
                if (ping.ok) "${ping.lossPercent}%" else "未测量"
            ),
            CheckItem(
                "MTU",
                when {
                    wifi.mtu == null -> CheckStatus.UNKNOWN
                    wifi.mtu >= 1400 -> CheckStatus.OK
                    else -> CheckStatus.WARN
                },
                wifi.mtu?.let { "$it 字节" } ?: "系统未上报"
            )
        )
    }

    /** 对每个目标并发做一次 3 包 ICMP；不等待 DNS 解析结果以外的数据 */
    suspend fun checkTargets(targets: List<TargetSpec>): List<TargetResult> = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext emptyList()
        coroutineScope {
            targets.map { t ->
                async(Dispatchers.IO) {
                    val r = runCatching { NetworkDiagnostics.ping(t.host, count = 3) }.getOrNull()
                    TargetResult(
                        label = t.label,
                        host = t.host,
                        rttAvg = r?.takeIf { it.ok && it.received > 0 }?.rttAvg,
                        lossPct = r?.takeIf { it.ok }?.lossPercent,
                        error = if (r == null || !r.ok) r?.error ?: "无响应" else null
                    )
                }
            }.map { it.await() }
        }
    }

    /** 依据网关与外网目标的通断做粗分类，只说明范围，不定性运营商责任 */
    fun locateFault(results: List<TargetResult>): String {
        if (results.isEmpty()) return "暂无检测结果"
        val gateway = results.firstOrNull { it.host.matches(Regex("10\\..*|192\\.168\\..*|172\\.(1[6-9]|2\\d|3[01])\\..*")) }
        val externals = results.filter { it !== gateway }
        return when {
            gateway != null && !gateway.ok -> "网关不可达，问题多半在本地网络或路由器"
            externals.isNotEmpty() && externals.none { it.ok } -> "网关可达但外部目标全部失败，问题在局域网之外（运营商或上游线路）"
            externals.any { !it.ok } -> "部分目标失败，可能是具体目标服务器或其链路异常"
            else -> "所有目标可达，当前未发现明显故障"
        }
    }
}
