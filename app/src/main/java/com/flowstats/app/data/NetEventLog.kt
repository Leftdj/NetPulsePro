package com.flowstats.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 事件分类：对应日志里的语义 */
enum class NetEventKind {
    ONLINE, OFFLINE, WIFI, CELLULAR, SIGNAL, DIAGNOSTIC, DNS, EXPORT, IP
}

/** 日志级别：对应截图里的「信息 / 成功 / 告警 / 错误 / 统计」标签 */
enum class EventLevel(val label: String) {
    INFO("信息"), SUCCESS("成功"), WARN("告警"), ERROR("错误"), STAT("统计")
}

fun NetEventKind.defaultLevel(): EventLevel = when (this) {
    NetEventKind.ONLINE -> EventLevel.SUCCESS
    NetEventKind.OFFLINE -> EventLevel.ERROR
    NetEventKind.WIFI, NetEventKind.CELLULAR, NetEventKind.DIAGNOSTIC,
    NetEventKind.DNS -> EventLevel.STAT
    NetEventKind.SIGNAL -> EventLevel.WARN
    NetEventKind.EXPORT -> EventLevel.SUCCESS
    NetEventKind.IP -> EventLevel.INFO
}

data class NetEvent(
    val time: Long,
    val kind: NetEventKind,
    val detail: String,
    val level: EventLevel = kind.defaultLevel()
)

/**
 * 运行日志（网络事件时间轴）。
 *
 * 记录来源均为真实事件：
 * - 系统回调：断网/恢复/制式切换
 * - 采样推断：信号突变、BSSID 漫游（文案标注「推断」）
 * - 用户/自动动作：诊断、DNS 对比、公网 IP、报表导出
 * 全部本地持久化，重启不丢。
 */
object NetEventLog {

    private const val MAX_EVENTS = 200
    private const val PREFS = "net_events"
    private const val KEY = "events"
    private val _events = MutableStateFlow<List<NetEvent>>(emptyList())
    val events: StateFlow<List<NetEvent>> = _events.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastBssid: String? = null
    private var lastKind: NetEventKind? = null
    private var appContext: Context? = null
    private var signalKey: String? = null
    private var signalBase: Int? = null

    @Synchronized
    fun start(context: Context) {
        if (callback != null) return
        val ctx = context.applicationContext
        appContext = ctx
        load(ctx)
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                push(NetEventKind.ONLINE, "网络已连接")
            }

            override fun onLost(network: Network) {
                push(NetEventKind.OFFLINE, "网络已断开")
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val kind = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetEventKind.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetEventKind.CELLULAR
                    else -> return
                }
                if (lastKind == kind) return
                lastKind = kind
                push(kind, if (kind == NetEventKind.WIFI) "切换到 Wi-Fi" else "切换到移动网络")
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
            .onSuccess { callback = cb }
    }

    /** BSSID 变化视为一次漫游 / 切换 AP */
    fun noteBssid(bssid: String?) {
        if (bssid.isNullOrBlank()) return
        val previous = lastBssid
        lastBssid = bssid
        if (previous != null && previous != bssid) {
            push(NetEventKind.WIFI, "AP 切换 → $bssid（漫游推断）")
        }
    }

    /** 信号强度变化超过阈值才记一条，避免刷屏 */
    fun noteSignal(rssi: Int?) {
        if (rssi == null) return
        val bucket = rssi / 12
        val key = "sig-$bucket"
        if (key == signalKey) return
        signalKey = key
        if (signalBase == null) {
            signalBase = rssi
            return
        }
        val delta = rssi - (signalBase ?: rssi)
        if (kotlin.math.abs(delta) >= 10) {
            signalBase = rssi
            push(
                NetEventKind.SIGNAL,
                if (delta > 0) "信号增强（当前 ${-rssi} dBm）" else "信号减弱（当前 ${-rssi} dBm）"
            )
        }
    }

    /** 记录一条真实动作日志（诊断 / DNS / 导出等），由 ViewModel 调用 */
    @Synchronized
    fun log(kind: NetEventKind, detail: String, level: EventLevel? = null) {
        val now = System.currentTimeMillis()
        val updated = (listOf(NetEvent(now, kind, detail, level ?: kind.defaultLevel())) + _events.value)
            .take(MAX_EVENTS)
        _events.value = updated
        appContext?.let { save(it, updated) }
    }

    @Synchronized
    private fun push(kind: NetEventKind, detail: String) {
        val list = _events.value
        val now = System.currentTimeMillis()
        // 同一秒内的同类事件合并，避免系统连续回调造成重复条目
        val last = list.firstOrNull()
        if (last != null && last.kind == kind && now - last.time < 1200) return
        val updated = (listOf(NetEvent(now, kind, detail)) + list).take(MAX_EVENTS)
        _events.value = updated
        appContext?.let { save(it, updated) }
    }

    @Synchronized
    fun clear() {
        _events.value = emptyList()
        appContext?.let { save(it, emptyList()) }
    }

    /** 估算日志占用字节数（detail UTF-8 + 时间戳字符） */
    fun estimatedBytes(events: List<NetEvent>): Int =
        events.sumOf { it.detail.toByteArray(Charsets.UTF_8).size + 24 }

    private fun load(context: Context) {
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return
            val arr = JSONArray(raw)
            val list = mutableListOf<NetEvent>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val kind = runCatching { NetEventKind.valueOf(o.optString("kind")) }
                    .getOrDefault(NetEventKind.IP)
                val level = runCatching { EventLevel.valueOf(o.optString("level")) }
                    .getOrDefault(kind.defaultLevel())
                list += NetEvent(o.optLong("time"), kind, o.optString("detail"), level)
            }
            _events.value = list.take(MAX_EVENTS)
        }
    }

    private fun save(context: Context, list: List<NetEvent>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(JSONObject().apply {
                    put("time", e.time)
                    put("kind", e.kind.name)
                    put("level", e.level.name)
                    put("detail", e.detail)
                })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply()
        }
    }

    fun stop() {
        val cb = callback ?: return
        val ctx = appContext ?: return
        runCatching {
            ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
        }
        callback = null
    }
}
