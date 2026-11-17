package com.flowstats.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetEventKind { ONLINE, OFFLINE, WIFI, CELLULAR, SIGNAL }

data class NetEvent(
    val time: Long,
    val kind: NetEventKind,
    val detail: String
)

/**
 * 网络事件时间轴。
 *
 * 记录来源有两类：系统回调（断网/恢复/制式切换）与信号突变采样。
 * 漫游没有系统事件可订阅，通过 BSSID 变化间接推断，所以文案会标注「推断」。
 */
object NetEventLog {

    private const val MAX_EVENTS = 80
    private val _events = MutableStateFlow<List<NetEvent>>(emptyList())
    val events: StateFlow<List<NetEvent>> = _events.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastBssid: String? = null
    private var lastKind: NetEventKind? = null

    private var appContext: Context? = null

    @Synchronized
    fun start(context: Context) {
        if (callback != null) return
        val ctx = context.applicationContext
        appContext = ctx
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
                if (delta > 0) "信号增强 ${-rssi} dBm" else "信号减弱 ${-rssi} dBm"
            )
        }
    }

    private var signalKey: String? = null
    private var signalBase: Int? = null

    private fun push(kind: NetEventKind, detail: String) {
        val list = _events.value
        val now = System.currentTimeMillis()
        // 同一秒内的同类事件合并，避免系统连续回调造成重复条目
        val last = list.firstOrNull()
        if (last != null && last.kind == kind && now - last.time < 1200) return
        val updated = (listOf(NetEvent(now, kind, detail)) + list).take(MAX_EVENTS)
        _events.value = updated
    }

    fun clear() {
        _events.value = emptyList()
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
