package com.flowstats.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowstats.app.data.AdvancedChecks
import com.flowstats.app.data.AppUsage
import com.flowstats.app.data.CheckItem
import com.flowstats.app.data.TargetResult
import com.flowstats.app.data.TargetSpec
import com.flowstats.app.data.DeviceStats
import com.flowstats.app.data.DnsProbe
import com.flowstats.app.data.LanReport
import com.flowstats.app.data.LanScanner
import com.flowstats.app.data.LinkGrading
import com.flowstats.app.data.LinkQualityHistory
import com.flowstats.app.data.LinkQualitySample
import com.flowstats.app.data.MemoryStat
import com.flowstats.app.data.NetEvent
import com.flowstats.app.data.NetEventKind
import com.flowstats.app.data.NetEventLog
import com.flowstats.app.data.NetworkDiagnostics
import com.flowstats.app.data.Period
import com.flowstats.app.data.PingReport
import com.flowstats.app.data.PublicIpInfo
import com.flowstats.app.data.SignalSpot
import com.flowstats.app.data.SignalSpotStore
import com.flowstats.app.data.SpeedMonitor
import com.flowstats.app.data.SpeedSample
import com.flowstats.app.data.Totals
import com.flowstats.app.data.UsageRepository
import com.flowstats.app.data.WifiDetail
import com.flowstats.app.data.WifiInfoProvider
import com.flowstats.app.data.WifiChannelScanner
import com.flowstats.app.data.WifiScanReport
import com.flowstats.app.data.ScanSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class SortBy(val label: String) { TOTAL("按总流量"), MOBILE("按移动流量"), WIFI("按 WiFi 流量"), NAME("按应用名称") }
enum class AppFilter(val label: String) { ALL("全部"), MOBILE("移动"), WIFI("WiFi"), LARGE("≥100MB") }

data class UiState(
    val period: Period = Period.DAY,
    val dayOffset: Int = 0,
    val dateLabel: String = "",
    val apps: List<AppUsage> = emptyList(),
    val summary: Totals = Totals(),
    val today: Totals = Totals(),
    val month: Totals = Totals(),
    val speed: SpeedSample = SpeedSample(),
    val history: List<SpeedSample> = emptyList(),
    val permissionGranted: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val message: String? = null,
    val query: String = "",
    val sortBy: SortBy = SortBy.TOTAL,
    val filter: AppFilter = AppFilter.ALL,
    val darkTheme: Boolean = false,
    val refreshHz: Int = 1000,
    /** DNS 对比自动刷新间隔（毫秒）：按用户要求固定为 1 秒 */
    val dnsIntervalMs: Long = 1_000L,
    val showSystem: Boolean = false,
    /** 隐藏无法解析名称/图标的应用（已卸载残留、独立后台进程） */
    val hideUnknown: Boolean = false,
    /** 每月套餐额度（GB），用于首页进度环与超额提醒 */
    val monthlyQuotaGb: Int = 300,
    /** 当前查看区间锚点的毫秒时间戳，供日历定位高亮 */
    val viewDate: Long = System.currentTimeMillis(),
    val dailyTrend: List<Pair<Totals, String>> = emptyList(),
    val hourly: List<Pair<Long, Long>> = emptyList(),
    val hourlyReady: Boolean = false,
    val trendReady: Boolean = false,
    /** Wi-Fi / 链路详情，拿不到的字段为 null */
    val wifi: WifiDetail = WifiDetail(),
    /** 蜂窝制式标签，Wi-Fi 下为 "Wi-Fi" */
    val accessType: String? = null,
    val memory: MemoryStat = MemoryStat(),
    val ping: PingReport = PingReport(),
    val pingRunning: Boolean = false,
    /** 上一次诊断完成的时间戳，界面据此显示「刚刚 / x 秒前」 */
    val lastPingAt: Long = 0,
    val dnsServer: String? = null,
    val dnsMs: Double? = null,
    /** DNS 解析对比：系统 DNS / 阿里 / 腾讯 */
    val dnsProbes: List<DnsProbe> = emptyList(),
    val dnsProbing: Boolean = false,
    val publicIp: String? = null,
    val publicIpv6: String? = null,
    val publicIpLoading: Boolean = false,
    val publicIpFailed: Boolean = false,
    /** 当前月历热力对应的月份（yyyy-M），与 monthHeat 配套 */
    val heatKey: String = "",
    /** 日历上每天的流量，dayOfMonth -> Totals */
    val monthHeat: Map<Int, Totals> = emptyMap(),
    val events: List<NetEvent> = emptyList(),
    val diagnoseOpen: Boolean = false,
    /** 网络页可见时，链路参数按 2 秒节奏强制刷新 */
    val networkVisible: Boolean = false,
    /** 链路质量历史（协商速率 / RSSI / 延迟抖动丢包），每轮测量合成一条 */
    val linkHistory: List<LinkQualitySample> = emptyList(),
    /** 检测历史：每次诊断 / 每 5 分钟自动快照存一条 */
    val diagnosticHistory: List<com.flowstats.app.data.DiagnosticRecord> = emptyList(),
    /** Wi-Fi 信道分析：系统扫描结果与分析结论 */
    val wifiScanReport: WifiScanReport? = null,
    val wifiScanHistory: List<ScanSummary> = emptyList(),
    val wifiScanning: Boolean = false,
    /** 不同位置的信号质量快照 */
    val signalSpots: List<SignalSpot> = emptyList(),
    /** 局域网扫描结果 */
    val lanReport: LanReport? = null,
    val lanScanning: Boolean = false,
    val lanProgress: Pair<Int, Int> = 0 to 0,
    /** 局域网设备的用户备注/收藏/忽略 */
    val deviceMeta: Map<String, com.flowstats.app.data.DeviceMeta> = emptyMap(),
    /** 一键体检与多目标监控 */
    val checkItems: List<CheckItem> = emptyList(),
    val checking: Boolean = false,
    val customTargets: List<TargetSpec> = emptyList(),
    val targetResults: List<TargetResult> = emptyList(),
    val targetChecking: Boolean = false
) {
    val visibleApps: List<AppUsage>
        get() {
            val q = query.trim().lowercase(Locale.ROOT)
            var result = apps.filter { app ->
                if (!showSystem && app.isSystem) return@filter false
                if (hideUnknown && app.isUnknown) return@filter false
                val matches = q.isEmpty() || app.label.lowercase(Locale.ROOT).contains(q) || app.packageName.lowercase(Locale.ROOT).contains(q)
                val passes = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.MOBILE -> app.mobile > 0
                    AppFilter.WIFI -> app.wifi > 0
                    AppFilter.LARGE -> app.total >= 100L * 1024 * 1024
                }
                matches && passes
            }
            result = when (sortBy) {
                SortBy.TOTAL -> result.sortedByDescending { it.total }
                SortBy.MOBILE -> result.sortedByDescending { it.mobile }
                SortBy.WIFI -> result.sortedByDescending { it.wifi }
                SortBy.NAME -> result.sortedBy { it.label }
            }
            return result
        }

    /** 内置目标随网关动态生成，自定义目标持久化 */
    val allTargets: List<TargetSpec>
        get() = AdvancedChecks.defaultTargets(wifi.gateway) + customTargets
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application.applicationContext)
    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()
    private var speedJob: Job? = null
    private var deviceJob: Job? = null
    private val prefs = application.getSharedPreferences("flowstats_prefs", android.content.Context.MODE_PRIVATE)

    init {
        // 启动流畅度：先在主线程只做轻量偏好读取，让首帧立即渲染；
        // 历史数据（SharedPreferences + JSON 解析）移到 IO 线程，不阻塞冷启动。
        _ui.value = _ui.value.copy(
            darkTheme = prefs.getBoolean("darkTheme", false),
            refreshHz = prefs.getInt("refreshHz", 1000),
            dnsIntervalMs = 1_000L,
            showSystem = prefs.getBoolean("showSystem", false),
            hideUnknown = prefs.getBoolean("hideUnknown", false),
            monthlyQuotaGb = prefs.getInt("monthlyQuotaGb", 300),
            linkHistory = LinkQualityHistory.load(application.applicationContext)
        )
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = application.applicationContext
            // 逐项 copy，避免整块覆盖掉 checkPermission / 设备监控并发写入的字段
            _ui.value = _ui.value.copy(wifiScanHistory = WifiChannelScanner.loadHistory(ctx))
            _ui.value = _ui.value.copy(signalSpots = SignalSpotStore.load(ctx))
            _ui.value = _ui.value.copy(deviceMeta = com.flowstats.app.data.LanDeviceStore.load(ctx))
            _ui.value = _ui.value.copy(customTargets = AdvancedChecks.loadCustom(ctx))
            _ui.value = _ui.value.copy(
                diagnosticHistory = com.flowstats.app.data.DiagnosticHistory.load(ctx)
            )
        }
        checkPermission()
        // 网络事件时间轴与设备指标不依赖流量权限，立即开始采集
        NetEventLog.start(application.applicationContext)
        NetEventLog.events
            .onEach { list ->
                val previous = _ui.value.events.firstOrNull()
                _ui.value = _ui.value.copy(events = list)
                val newest = list.firstOrNull()
                // 网络恢复的那一刻立刻补测一次，不用等下一轮轮询
                if (newest != null && newest.time != previous?.time &&
                    newest.kind == NetEventKind.ONLINE
                ) {
                    viewModelScope.launch(Dispatchers.IO) {
                        runPingRound(quick = true, withDns = true)
                    }
                    // 出口 IP 可能已经变了，清掉缓存重新取
                    _ui.value = _ui.value.copy(publicIp = null, publicIpv6 = null)
                    refreshPublicIp(force = true)
                }
            }
            .launchIn(viewModelScope)
        startDeviceMonitor()
    }

    /**
     * Wi-Fi 详情与内存占用轮询。
     * Wi-Fi 侧的读写本身很轻，但系统驱动的链路速率并不是每次查询都会更新，
     * 所以这里按刷新频率轮询、拿系统当前上报值，不做任何平滑或插值。
     */
    private fun startDeviceMonitor() {
        deviceJob?.cancel()
        deviceJob = viewModelScope.launch(Dispatchers.IO) {
            var lastIp: String? = null
            var lastDns: String? = null
            var lastLinkMbps: Int? = null
            while (true) {
                val context = getApplication<Application>()
                runCatching {
                    val wifi = WifiInfoProvider.read(context)
                    val memory = DeviceStats.memory(context)
                    val access = WifiInfoProvider.cellularLabel(context)
                    NetEventLog.noteBssid(wifi.bssid)
                    NetEventLog.noteSignal(wifi.rssi)
                    val linkNow = wifi.rxLinkMbps ?: wifi.linkSpeedMbps
                    if (lastLinkMbps != null && linkNow != null && lastLinkMbps!! > 0 &&
                        linkNow < lastLinkMbps!! * 0.8) {
                        NetEventLog.log(
                            com.flowstats.app.data.NetEventKind.SIGNAL,
                            "协商速率突降：$lastLinkMbps → $linkNow Mbps（${wifi.band ?: "未知频段"}）",
                            com.flowstats.app.data.EventLevel.WARN
                        )
                    }
                    lastLinkMbps = linkNow
                    val ipNow = wifi.ipv4?.substringBefore('/')
                    val dnsNow = wifi.dns.firstOrNull()
                    if (lastIp != null && ipNow != null && ipNow != lastIp) {
                        NetEventLog.log(com.flowstats.app.data.NetEventKind.IP, "IPv4 变化：$lastIp → $ipNow")
                    }
                    if (lastDns != null && dnsNow != null && dnsNow != lastDns) {
                        NetEventLog.log(com.flowstats.app.data.NetEventKind.DNS, "DNS 变化：$lastDns → $dnsNow")
                    }
                    lastIp = ipNow
                    lastDns = dnsNow
                    _ui.value = _ui.value.copy(
                        wifi = wifi,
                        memory = memory,
                        accessType = access
                    )
                }
                // 设备指标（Wi-Fi/内存/事件）2 秒足够，与链路采样对齐；
                // 不跟随网速采样频率（可低至 0.5 秒），避免首屏每秒全页重组造成卡顿。
                // 实时速率由 SpeedMonitor 单独以 refreshHz 更新，不受此处影响。
                delay(2_000L)
            }
        }
    }

    private val pingBusy = AtomicBoolean(false)
    private val lanStopFlag = AtomicBoolean(false)
    private var lastLatencyBad = false
    private var lastLossBad = false
    private var pingJob: Job? = null
    private var pingRound = 0
    private var lastDnsCompareAt = 0L

    /**
     * 前台自动测量开关。
     * 每 2 秒测一轮轻量 ICMP（4 包）；进入网络页时 DNS 对比随每轮自动跑，
     * 公网 IP 每 30 秒刷新一次（外部 HTTP 请求不做 2 秒轰炸）。后台不测。
     */
    fun setForeground(value: Boolean) {
        if (value) startPingLoop() else {
            pingJob?.cancel(); pingJob = null
        }
    }

    /** 网络页可见时切到 2 秒高频刷新，离开后恢复默认轮询 */
    fun setNetworkVisible(visible: Boolean) {
        val prev = _ui.value.networkVisible
        _ui.value = _ui.value.copy(networkVisible = visible)
        if (visible && !prev) {
            // 刚进页面立刻补一轮，不用等下一次定时器
            viewModelScope.launch(Dispatchers.IO) {
                val wifi = WifiInfoProvider.read(getApplication())
                _ui.value = _ui.value.copy(wifi = wifi)
                runPingRound(quick = true, withDns = true)
                runDnsCompare()
                lastDnsCompareAt = System.currentTimeMillis()
            }
        }
    }

    private fun startPingLoop() {
        if (pingJob?.isActive == true) return
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runPingRound(quick = true, withDns = pingRound % 3 == 0)
                pingRound++
                // 每 150 轮（约 5 分钟）自动存一条检测快照到历史
                if (pingRound % 150 == 0 && _ui.value.ping.ok) recordDiagnostic(_ui.value.ping)
                if (_ui.value.networkVisible) {
                    // DNS 对比是对多个 DNS 服务器发起真实 UDP 解析，按可配置间隔节流（默认 15 秒），
                    // 不再每 2 秒轰炸；内部有 dnsProbing 防重
                    val now = System.currentTimeMillis()
                    if (now - lastDnsCompareAt >= _ui.value.dnsIntervalMs) {
                        runDnsCompare()
                        lastDnsCompareAt = now
                    }
                    // 公网 IP 是外部 HTTP，30 秒刷新一次，避免无效请求
                    if (pingRound % 15 == 0) refreshPublicIp(force = true)
                }
                delay(2_000L)
            }
        }
    }

    private suspend fun runPingRound(quick: Boolean, withDns: Boolean) {
        if (!pingBusy.compareAndSet(false, true)) return
        try {
            _ui.value = _ui.value.copy(pingRunning = true)
            val report = runCatching {
                if (quick) NetworkDiagnostics.pingQuick() else NetworkDiagnostics.ping()
            }.getOrElse { PingReport(error = it.message ?: "测量失败") }

            var server = _ui.value.dnsServer
            var dnsMs = _ui.value.dnsMs
            if (withDns) {
                val dns = runCatching { NetworkDiagnostics.dnsLatency(_ui.value.wifi.dns) }.getOrNull()
                if (dns != null) {
                    server = dns.first
                    dnsMs = dns.second
                }
            }
            val wifiNow = _ui.value.wifi
            val latencyBad = report.ok && report.rttAvg >= 200
            if (latencyBad && !lastLatencyBad) {
                NetEventLog.log(com.flowstats.app.data.NetEventKind.DIAGNOSTIC,
                    "延迟异常：平均 ${report.rttAvg.toInt()} ms（连续超过 200 ms）", com.flowstats.app.data.EventLevel.WARN)
            }
            lastLatencyBad = latencyBad
            val lossBad = report.ok && report.lossPercent >= 10
            if (lossBad && !lastLossBad) {
                NetEventLog.log(com.flowstats.app.data.NetEventKind.DIAGNOSTIC,
                    "丢包异常：${report.lossPercent}%（连续出现丢包）", com.flowstats.app.data.EventLevel.WARN)
            }
            lastLossBad = lossBad
            _ui.value = _ui.value.copy(
                ping = report,
                pingRunning = false,
                lastPingAt = System.currentTimeMillis(),
                dnsServer = server,
                dnsMs = dnsMs
            )
            // 每轮测量合成一条链路质量历史（全部为真实采样，无字段则留空）
            // 频段/频宽一起入档：2.4G 与 5G 的协商速率上限不同，评级必须按各自基准算
            val sample = LinkQualitySample(
                time = System.currentTimeMillis(),
                linkMbps = wifiNow.rxLinkMbps ?: wifiNow.linkSpeedMbps,
                rssi = wifiNow.rssi,
                latencyMs = report.rttAvg.takeIf { report.ok },
                jitterMs = report.jitter.takeIf { report.ok },
                lossPct = report.lossPercent.toDouble().takeIf { report.ok },
                isWifi = wifiNow.isWifi,
                band = wifiNow.band,
                widthMhz = wifiNow.channelWidthMhz,
                ceilingMbps = LinkGrading.referenceCeiling(
                    wifiNow.channelWidthMhz,
                    wifiNow.band,
                    wifiNow.maxRxMbps
                )
            )
            val updated = LinkQualityHistory.add(getApplication(), sample)
            _ui.value = _ui.value.copy(linkHistory = updated)
        } finally {
            pingBusy.set(false)
        }
    }

    fun runAdvancedChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(checking = true)
            val cur = _ui.value
            val items = AdvancedChecks.runChecks(
                getApplication(), cur.wifi, cur.ping, cur.dnsMs, cur.publicIp, cur.publicIpv6
            )
            _ui.value = _ui.value.copy(checkItems = items, checking = false)
        }
    }

    fun runTargetChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(targetChecking = true)
            val results = AdvancedChecks.checkTargets(_ui.value.allTargets)
            _ui.value = _ui.value.copy(targetResults = results, targetChecking = false)
        }
    }

    fun addTarget(label: String, host: String) {
        _ui.value = _ui.value.copy(
            customTargets = AdvancedChecks.addCustom(getApplication(), label, host)
        )
    }

    fun removeTarget(host: String) {
        _ui.value = _ui.value.copy(
            customTargets = AdvancedChecks.removeCustom(getApplication(), host),
            targetResults = _ui.value.targetResults.filterNot { it.host == host }
        )
    }

    /** 局域网扫描：ICMP 探测 + ARP 表 + 端口探测，全部在 IO 线程执行 */
    fun scanLan() {
        lanStopFlag.set(false)
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(lanScanning = true, lanProgress = 0 to 0)
            val report = runCatching {
                LanScanner.scan(
                    getApplication(),
                    onProgress = { done, total, _ ->
                        _ui.value = _ui.value.copy(lanProgress = done to total)
                    },
                    shouldStop = { lanStopFlag.get() }
                )
            }.getOrElse { LanReport(error = "扫描失败：${it.message ?: "未知错误"}") }
            _ui.value = _ui.value.copy(lanReport = report, lanScanning = false)
        }
    }

    fun stopLanScan() {
        lanStopFlag.set(true)
    }

    /** 更新设备备注/收藏/忽略，持久化后同步到状态 */
    fun updateDeviceMeta(ip: String, transform: (com.flowstats.app.data.DeviceMeta) -> com.flowstats.app.data.DeviceMeta) {
        val map = com.flowstats.app.data.LanDeviceStore.update(getApplication(), ip, transform)
        _ui.value = _ui.value.copy(deviceMeta = map)
    }

    /** 单设备网络质量实测：真实 ping 3 包，结果通过回调返回，不缓存不估算 */
    fun pingDevice(ip: String, onResult: (com.flowstats.app.data.PingReport) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = runCatching {
                com.flowstats.app.data.NetworkDiagnostics.ping(ip, count = 3)
            }.getOrElse { com.flowstats.app.data.PingReport(error = it.message ?: "测量失败") }
            withContext(Dispatchers.Main) { onResult(report) }
        }
    }

    /** 保存当前位置的信号快照：取最近 30 条真实 RSSI 采样统计，样本不足不生成 */
    fun saveSignalSpot(label: String) {
        val values = _ui.value.linkHistory.mapNotNull { it.rssi }.take(30)
        if (values.isEmpty()) return
        val spot = SignalSpot(
            time = System.currentTimeMillis(),
            label = label,
            ssid = _ui.value.wifi.ssid,
            avgRssi = (values.sum() / values.size.toDouble()).toInt(),
            minRssi = values.minOrNull() ?: values.first(),
            count = values.size
        )
        _ui.value = _ui.value.copy(signalSpots = SignalSpotStore.add(getApplication(), spot))
    }

    fun deleteSignalSpot(time: Long) {
        _ui.value = _ui.value.copy(
            signalSpots = SignalSpotStore.remove(getApplication(), time)
        )
    }

    /** 手动扫描周围 Wi-Fi AP：读取系统扫描缓存并请求刷新；权限不足/被节流时如实报错 */
    fun scanWifiChannels() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(wifiScanning = true)
            val report = runCatching {
                WifiChannelScanner.scan(getApplication(), _ui.value.wifi.bssid)
            }.getOrElse { WifiScanReport(error = "扫描失败：${it.message ?: "未知错误"}") }
            val history = WifiChannelScanner.record(getApplication(), report)
            _ui.value = _ui.value.copy(
                wifiScanReport = report,
                wifiScanHistory = history,
                wifiScanning = false
            )
        }
    }

    /** 手动「重新测量」：包数更多，并顺带刷新 DNS 对比 */
    fun runDiagnostics() {
        if (!pingBusy.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _ui.value = _ui.value.copy(pingRunning = true)
                val report = runCatching { NetworkDiagnostics.ping() }
                    .getOrElse { PingReport(error = it.message ?: "测量失败") }
                val dns = runCatching { NetworkDiagnostics.dnsLatency(_ui.value.wifi.dns) }.getOrNull()
                _ui.value = _ui.value.copy(
                    ping = report,
                    pingRunning = false,
                    lastPingAt = System.currentTimeMillis(),
                    dnsServer = dns?.first ?: _ui.value.dnsServer,
                    dnsMs = dns?.second ?: _ui.value.dnsMs
                )
                runCatching { NetworkDiagnostics.dnsCompare(_ui.value.wifi.dns) }
                    .getOrNull()?.let { _ui.value = _ui.value.copy(dnsProbes = it) }
                // 真实诊断结果记入运行日志
                val summary = if (report.ok)
                    "手动诊断完成：延迟 ${report.rttAvg.toInt()} ms / 抖动 ${report.jitter.toInt()} ms / 丢包 ${report.lossPercent}%"
                else "手动诊断失败：${report.error ?: "无有效响应"}"
                NetEventLog.log(
                    com.flowstats.app.data.NetEventKind.DIAGNOSTIC,
                    summary,
                    if (report.ok) com.flowstats.app.data.EventLevel.SUCCESS else com.flowstats.app.data.EventLevel.ERROR
                )
                recordDiagnostic(report)
            } finally {
                pingBusy.set(false)
            }
        }
    }

    /** DNS 解析对比：判断是本地 DNS 慢还是全网都慢 */
    fun runDnsCompare() {
        if (_ui.value.dnsProbing) return
        _ui.value = _ui.value.copy(dnsProbing = true)
        viewModelScope.launch(Dispatchers.IO) {
            val probes = runCatching { NetworkDiagnostics.dnsCompare(_ui.value.wifi.dns) }
                .getOrDefault(emptyList())
            _ui.value = _ui.value.copy(dnsProbes = probes, dnsProbing = false)
        }
    }

    fun refreshPublicIp(force: Boolean = false) {
        if (_ui.value.publicIpLoading) return
        if (!force && (_ui.value.publicIp != null || _ui.value.publicIpv6 != null)) return
        _ui.value = _ui.value.copy(publicIpLoading = true, publicIpFailed = false)
        viewModelScope.launch(Dispatchers.IO) {
            val info: PublicIpInfo = runCatching { NetworkDiagnostics.publicIpInfo() }
                .getOrElse { PublicIpInfo(error = it.message ?: "获取失败") }
            _ui.value = _ui.value.copy(
                publicIp = info.ipv4 ?: _ui.value.publicIp,
                publicIpv6 = info.ipv6 ?: _ui.value.publicIpv6,
                publicIpLoading = false,
                publicIpFailed = !info.ok
            )
        }
    }

    fun setDiagnoseOpen(open: Boolean) {
        _ui.value = _ui.value.copy(diagnoseOpen = open)
        if (open) {
            refreshPublicIp()
            if (_ui.value.dnsProbes.isEmpty()) runDnsCompare()
        }
    }

    /** 进入网络页时调用：公网 IP、DNS 对比、日历不需要，这里只做网络相关的首次拉取 */
    fun onNetworkVisible() {
        refreshPublicIp()
        if (_ui.value.dnsProbes.isEmpty()) runDnsCompare()
        if (_ui.value.ping.samples.isEmpty()) runDiagnostics()
    }

    private val heatCache = mutableMapOf<String, Map<Int, Totals>>()

    /**
     * 月历热力：按月缓存，翻月不重复扫系统统计库。
     * 每天两次 summary 查询，一个月最多 31 天，放在 IO 线程做。
     */
    fun loadMonthHeat(year: Int, month: Int) {
        val key = "$year-${month + 1}"
        val now = java.util.Calendar.getInstance()
        val isCurrentMonth = year == now.get(java.util.Calendar.YEAR) &&
            month == now.get(java.util.Calendar.MONTH)
        // 历史月份命中缓存就不再查；当月每次打开都重新算（今天的量还在涨）
        if (!isCurrentMonth) {
            if (_ui.value.heatKey == key) return
            heatCache[key]?.let {
                _ui.value = _ui.value.copy(heatKey = key, monthHeat = it)
                return
            }
        }
        _ui.value = _ui.value.copy(heatKey = key, monthHeat = emptyMap())
        viewModelScope.launch(Dispatchers.IO) {
            val map = runCatching { repository.monthDailyTotals(year, month) }
                .getOrDefault(emptyMap())
            if (!isCurrentMonth) heatCache[key] = map
            // 用户可能已经翻到别的月份了，别把旧月份的数据盖上去
            if (_ui.value.heatKey == key) _ui.value = _ui.value.copy(monthHeat = map)
        }
    }

    fun clearEvents() = NetEventLog.clear()

    /** 导出三份流量 CSV（日流量/应用排行/时段分布）到下载目录 */
    fun exportCsv(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val message = runCatching {
                val cur = _ui.value
                val summary = com.flowstats.app.data.CsvExporter.export(
                    getApplication(), repository, cur.period, cur.dayOffset
                )
                NetEventLog.log(com.flowstats.app.data.NetEventKind.EXPORT, "导出流量报表 ${summary.files} 份 → ${summary.location}")
                "已导出 ${summary.files} 份报表 → ${summary.location}"
            }.getOrElse { "导出失败：${it.message ?: "未知错误"}" }
            withContext(Dispatchers.Main) { onResult(message) }
        }
    }

    /** 导出运行日志 CSV */
    fun exportLogs(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val events = _ui.value.events
            if (events.isEmpty()) {
                withContext(Dispatchers.Main) { onResult("暂无日志可导出") }
                return@launch
            }
            var location = ""
            runCatching {
                com.flowstats.app.data.CsvExporter.exportLogs(getApplication(), events) { location = it }
                NetEventLog.log(com.flowstats.app.data.NetEventKind.EXPORT, "导出运行日志 ${events.size} 条 → $location")
            }.onFailure { location = "导出失败：${it.message ?: "未知错误"}" }
            withContext(Dispatchers.Main) { onResult(location) }
        }
    }

    /** 清空链路质量历史 */
    fun clearLinkHistory() {
        LinkQualityHistory.clear(getApplication())
        _ui.value = _ui.value.copy(linkHistory = emptyList())
    }

    /** 记一条真实的检测快照到历史 */
    private fun recordDiagnostic(report: PingReport) {
        val cur = _ui.value
        val rec = com.flowstats.app.data.DiagnosticRecord(
            time = System.currentTimeMillis(),
            host = report.host.ifBlank { cur.ping.host.ifBlank { "223.5.5.5" } },
            rttAvg = report.rttAvg.takeIf { report.ok },
            jitter = report.jitter.takeIf { report.ok },
            lossPercent = report.lossPercent.takeIf { report.ok },
            ok = report.ok,
            dnsServer = if (report.ok) (cur.dnsServer ?: cur.wifi.dns.firstOrNull()) else null,
            dnsMs = if (report.ok) cur.dnsMs else null,
            rssi = cur.wifi.rssi,
            linkMbps = cur.wifi.rxLinkMbps ?: cur.wifi.linkSpeedMbps,
            isWifi = cur.wifi.isWifi
        )
        _ui.value = _ui.value.copy(
            diagnosticHistory = com.flowstats.app.data.DiagnosticHistory.add(getApplication(), rec)
        )
    }

    fun clearDiagnosticHistory() {
        com.flowstats.app.data.DiagnosticHistory.clear(getApplication())
        _ui.value = _ui.value.copy(diagnosticHistory = emptyList())
    }

    /** 导出检测历史 CSV */
    fun exportDiagnosticHistory(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val records = _ui.value.diagnosticHistory
            if (records.isEmpty()) {
                withContext(Dispatchers.Main) { onResult("暂无检测历史可导出") }
                return@launch
            }
            var location = ""
            runCatching {
                com.flowstats.app.data.CsvExporter.exportDiagnostics(getApplication(), records) { location = it }
            }.onFailure { location = "导出失败：${it.message ?: "未知错误"}" }
            withContext(Dispatchers.Main) { onResult(location) }
        }
    }

    /** 刷新频率改变时重启采样 */
    private fun startSpeedMonitor() {
        speedJob?.cancel()
        speedJob = SpeedMonitor.flow(getApplication(), _ui.value.refreshHz.toLong())
            .onEach { sample ->
                val prev = _ui.value.history.lastOrNull()
                // 偶发的异常尖峰（超过上一秒 50 倍）视为无效采样，避免图表被打飞
                val clean = if (prev != null && prev.download > 0f &&
                    sample.download > prev.download * 50f && sample.download > 100f * 1024 * 1024) {
                    sample.copy(download = prev.download, upload = prev.upload)
                } else sample
                _ui.value = _ui.value.copy(
                    speed = clean,
                    history = (_ui.value.history + clean).takeLast(72)
                )
            }
            .launchIn(viewModelScope)
    }

    fun checkPermission() {
        val granted = repository.hasUsagePermission()
        val wasGranted = _ui.value.permissionGranted
        _ui.value = _ui.value.copy(permissionGranted = granted)
        if (granted && !wasGranted) {
            startSpeedMonitor()
            reload()
        }
    }

    fun setPeriod(period: Period) {
        val current = _ui.value
        if (current.period == period) return
        // 偏移量单位随周期变化（周/月/年），必须用锚点重新换算。
        // 锚点选择：dayOffset == 0 表示当前周期（必然包含今天），直接以今天为锚点；
        // 只有翻到历史周期时才用区间起点。
        // 注意：不能用 now in start..end 判断「是否包含今天」——rangeFor 把区间结束截断为 now，
        // 该判断恒为 false，导致锚点总是回退到区间起点；年的起点是 1 月 1 日，
        // 于是「年 → 月」落到 1 月、「年 → 周/日」落到元旦，这正是之前 bug 的根因。
        val (start, _) = repository.rangeFor(current.period, current.dayOffset)
        val now = System.currentTimeMillis()
        val anchorMillis = if (current.dayOffset == 0) now else start
        val offset = repository.offsetFor(period, anchorMillis)
        _ui.value = current.copy(
            period = period,
            dayOffset = offset,
            dateLabel = repository.dateLabel(period, offset),
            viewDate = repository.rangeFor(period, offset).first
        )
        reload()
    }

    fun shiftDate(delta: Int) {
        val next = _ui.value.dayOffset + delta
        if (next > 0) return
        val period = _ui.value.period
        _ui.value = _ui.value.copy(
            dayOffset = next,
            dateLabel = repository.dateLabel(period, next),
            viewDate = repository.rangeFor(period, next).first
        )
        reload()
    }

    /** 从日历中选中某一天（month 为 Calendar 月份 0-11），换算成当前周期下的偏移 */
    fun pickDate(year: Int, month: Int, day: Int) {
        val today = java.util.Calendar.getInstance(java.util.Locale.CHINA)
        val picked = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            set(year, month, day, 0, 0, 0)
            clear(java.util.Calendar.MILLISECOND)
        }
        // 不允许选未来（以当天 0 点为界）
        val todayStart = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            clear(java.util.Calendar.MILLISECOND)
        }
        if (picked.timeInMillis > todayStart.timeInMillis) return

        // 统一走仓库的换算逻辑，保证各周期口径一致
        val period = _ui.value.period
        val offset = repository.offsetFor(period, picked.timeInMillis)
        _ui.value = _ui.value.copy(
            dayOffset = offset,
            dateLabel = repository.dateLabel(period, offset),
            viewDate = repository.rangeFor(period, offset).first
        )
        reload()
    }

    private fun daysBetween(a: java.util.Calendar, b: java.util.Calendar): Int {
        val start = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = a.timeInMillis
            set(java.util.Calendar.HOUR_OF_DAY, 12)
        }
        val end = java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = b.timeInMillis
            set(java.util.Calendar.HOUR_OF_DAY, 12)
        }
        return Math.round((end.timeInMillis - start.timeInMillis) / 86400000.0).toInt()
    }

    private fun weekStart(cal: java.util.Calendar): java.util.Calendar =
        java.util.Calendar.getInstance(java.util.Locale.CHINA).apply {
            timeInMillis = cal.timeInMillis
            firstDayOfWeek = java.util.Calendar.MONDAY
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 12)
        }

    fun setQuery(query: String) { _ui.value = _ui.value.copy(query = query) }
    fun setSort(sort: SortBy) { _ui.value = _ui.value.copy(sortBy = sort) }
    fun setFilter(filter: AppFilter) { _ui.value = _ui.value.copy(filter = filter) }
    fun toggleTheme() {
        val value = !_ui.value.darkTheme
        _ui.value = _ui.value.copy(darkTheme = value)
        prefs.edit().putBoolean("darkTheme", value).apply()
    }
    fun setRefreshHz(hz: Int) {
        if (hz == _ui.value.refreshHz) return
        _ui.value = _ui.value.copy(refreshHz = hz)
        prefs.edit().putInt("refreshHz", hz).apply()
        startSpeedMonitor()
    }
    fun toggleSystem() {
        val value = !_ui.value.showSystem
        _ui.value = _ui.value.copy(showSystem = value)
        prefs.edit().putBoolean("showSystem", value).apply()
    }
    fun toggleHideUnknown() {
        val value = !_ui.value.hideUnknown
        _ui.value = _ui.value.copy(hideUnknown = value)
        prefs.edit().putBoolean("hideUnknown", value).apply()
    }
    fun setQuotaGb(gb: Int) {
        if (gb <= 0) return
        _ui.value = _ui.value.copy(monthlyQuotaGb = gb)
        prefs.edit().putInt("monthlyQuotaGb", gb).apply()
    }

    private var reloadJob: Job? = null
    private var reloadToken = 0

    fun reload() {
        // 取消上一次查询：NetworkStatsManager 是阻塞调用，取消不一定能立即打断，
        // 因此写入状态前还要校验周期/偏移是否仍是本次请求，避免旧结果覆盖新页面。
        reloadJob?.cancel()
        val token = ++reloadToken
        val reqPeriod = _ui.value.period
        val reqOffset = _ui.value.dayOffset
        val reqLabel = repository.dateLabel(reqPeriod, reqOffset)
        val granted = _ui.value.permissionGranted
        _ui.value = _ui.value.copy(loading = true, refreshing = true, dateLabel = reqLabel)
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val (start, end) = repository.rangeFor(reqPeriod, reqOffset)
                val (apps, summary) = repository.loadAppUsages(start, end)
                val nowRange = repository.rangeFor(Period.DAY, 0)
                val monthRange = repository.rangeFor(Period.MONTH, 0)
                val today = repository.totalsFor(nowRange.first, nowRange.second)
                val month = repository.totalsFor(monthRange.first, monthRange.second)
                if (token != reloadToken) return@launch
                _ui.value = _ui.value.copy(
                    apps = apps, summary = summary, today = today, month = month, dateLabel = reqLabel,
                    viewDate = start,
                    loading = false, refreshing = false,
                    message = if (apps.isEmpty() && granted) "该时间段暂无流量记录" else null
                )
                // 历史数据较慢，分批加载，避免主列表卡顿
                val trend = repository.dailyTotals(7)
                if (token != reloadToken) return@launch
                _ui.value = _ui.value.copy(dailyTrend = trend, trendReady = true)
                val hourly = repository.hourlyTotals()
                if (token != reloadToken) return@launch
                _ui.value = _ui.value.copy(hourly = hourly, hourlyReady = true)
            } catch (e: Exception) {
                if (token != reloadToken) return@launch
                _ui.value = _ui.value.copy(
                    loading = false, refreshing = false,
                    message = "读取失败：${e.message ?: "未知错误"}"
                )
            }
        }
    }


    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }
}
