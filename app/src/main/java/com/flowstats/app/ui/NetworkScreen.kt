package com.flowstats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowstats.app.data.NetEvent
import com.flowstats.app.data.NetEventKind
import com.flowstats.app.data.NetworkDiagnostics
import com.flowstats.app.data.WifiDetail
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Slate
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import com.flowstats.app.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkScreen(
    state: UiState,
    onRunDiagnostics: () -> Unit,
    onClearEvents: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRefreshPublicIp: () -> Unit = {},
    onRunDnsCompare: () -> Unit = {},
    onClearLinkHistory: () -> Unit = {},
    onExportLogs: () -> Unit = {}
) {
    val wifi = state.wifi
    var logViewerOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier.statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 2.dp)
        ) {
            Text(
                "网络",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (wifi.isWifi) "Wi-Fi 链路详情与质量诊断" else "移动网络链路详情与质量诊断",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))
            ConnectionCard(state, wifi, onOpenLocationSettings)

            // ── Wi-Fi 专属参数 ──────────────────────────
            if (wifi.isWifi) {
                Spacer(Modifier.height(14.dp))
                SectionTitle("无线链路")
                Spacer(Modifier.height(9.dp))
                SoftCard(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    corner = 22.dp,
                    elevation = 3.dp
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        InfoRow("频段", wifi.band)
                        RowDivider()
                        InfoRow("Wi-Fi 代际", wifi.standard)
                        RowDivider()
                        InfoRow(
                            "协商速率（接收）",
                            wifi.rxLinkMbps?.let { "$it Mbps" } ?: wifi.linkSpeedMbps?.let { "$it Mbps" }
                        )
                        RowDivider()
                        InfoRow("协商速率（发送）", wifi.txLinkMbps?.let { "$it Mbps" })
                        RowDivider()
                        InfoRow("协商速率上限", wifi.maxRxMbps?.let { "$it Mbps" })
                        RowDivider()
                        InfoRow("信道", wifi.channel?.let { "CH $it" })
                        RowDivider()
                        InfoRow("频宽", wifi.channelWidthMhz?.let { "$it MHz" }, fallback = "需扫描授权")
                        RowDivider()
                        InfoRow("信号强度", wifi.rssi?.let { "$it dBm" }, fallback = "需要定位权限")
                        RowDivider()
                        InfoRow("BSSID", wifi.bssid, mono = true, fallback = "需要定位权限")

                        Spacer(Modifier.height(6.dp))
                        RowDivider()
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "以下三项 Android 未提供公开接口，不做估算填充",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        InfoRow("MCS Index", null)
                        InfoRow("NSS 空间流", null)
                        InfoRow("SNR 信噪比", null)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ── 网络参数 ────────────────────────────────
            Spacer(Modifier.height(14.dp))
            SectionTitle(
                "网络参数",
                trailing = if (state.networkVisible) "每 2 秒自动刷新" else "来自 LinkProperties"
            )
            Spacer(Modifier.height(9.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                corner = 22.dp,
                elevation = 3.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    InfoRow("IPv4", wifi.ipv4, mono = true, fallback = "未分配")
                    RowDivider()
                    InfoRow("IPv6", wifi.ipv6, mono = true, fallback = "未分配")
                    RowDivider()
                    InfoRow(
                        "DNS",
                        wifi.dns.takeIf { it.isNotEmpty() }?.joinToString("  "),
                        mono = true,
                        fallback = "未配置"
                    )
                    RowDivider()
                    InfoRow("MTU", wifi.mtu?.let { "$it" }, fallback = "系统未上报")
                    RowDivider()
                    InfoRow("默认网关", wifi.gateway, mono = true, fallback = "系统未暴露")
                    RowDivider()
                    InfoRow("接口", wifi.interfaceName, mono = true, fallback = "系统未暴露")
                    RowDivider()
                    InfoRow(
                        "私有 DNS",
                        wifi.privateDns?.takeIf { it.isNotBlank() } ?: "未启用",
                        mono = true
                    )
                    RowDivider()
                    PublicIpRow(
                        state = state,
                        onRetry = onRefreshPublicIp
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── DNS 解析对比 ────────────────────────────
            Spacer(Modifier.height(14.dp))
            SectionTitle(
                "DNS 解析对比",
                trailing = if (state.networkVisible) "每 2 秒自动刷新"
                else if (state.dnsProbing) "测试中…" else "同一条 A 记录"
            )
            Spacer(Modifier.height(9.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                corner = 22.dp,
                elevation = 3.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (state.dnsProbes.isEmpty()) {
                        Text(
                            if (state.dnsProbing) "正在向各 DNS 服务器查询…"
                            else "点按下面「重新测试」，对比系统 DNS 与公共 DNS 的解析延迟。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val best = state.dnsProbes.filter { it.ms != null }.minByOrNull { it.ms!! }?.server
                        state.dnsProbes.forEachIndexed { index, probe ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        probe.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        probe.server,
                                        fontSize = 9.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                                if (probe.server == best) {
                                    Text(
                                        "最快",
                                        fontSize = 8.5.sp,
                                        color = Success,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Success.copy(alpha = 0.12f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                }
                                Text(
                                    probe.ms?.let { String.format(Locale.US, "%.0f ms", it) } ?: "无响应",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        probe.ms == null -> Danger
                                        probe.ms > 100 -> Orange
                                        else -> Success
                                    }
                                )
                            }
                            if (index != state.dnsProbes.lastIndex) RowDivider()
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.dnsProbing) "测试中…" else if (state.networkVisible) "手动重测" else "重新测试",
                        fontSize = 11.sp,
                        color = Blue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable(enabled = !state.dnsProbing) { onRunDnsCompare() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }

            // ── 诊断 ────────────────────────────────────
            Spacer(Modifier.height(14.dp))
            SectionTitle(
                "网络质量",
                trailing = if (state.networkVisible) "每 2 秒自动刷新"
                else if (state.pingRunning) "测量中…" else "ICMP 实测"
            )
            Spacer(Modifier.height(9.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                corner = 22.dp,
                elevation = 3.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    val ping = state.ping
                    val score = remember(ping) { NetworkDiagnostics.stabilityScore(ping) }
                    val scoreColor = when {
                        score >= 80 -> Success
                        score >= 60 -> Orange
                        else -> Danger
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp)) {
                            RingChart(
                                progress = score / 100f,
                                color = scoreColor,
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 7f
                            )
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (ping.samples.isEmpty()) "--" else "$score",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = scoreColor
                                    )
                                    Text(
                                        "稳定性",
                                        fontSize = 7.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stabilityLabel(score, ping.samples.isEmpty()),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            val routerHint = state.wifi.gateway?.let { "网关 $it" } ?: "目标 ${ping.host}"
                            Text(
                                routerHint,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (ping.error != null) ping.error
                                else "${ping.received}/${ping.sent} 包 · 丢包 ${ping.lossPercent}% · 抖动 ${String.format(Locale.US, "%.1f", ping.jitter)} ms",
                                fontSize = 10.sp,
                                color = if (ping.error != null) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    SparkBars(
                        samples = ping.samples,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        color = scoreColor
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MiniStat("最小", if (ping.samples.isEmpty()) "--" else String.format(Locale.US, "%.0f ms", ping.rttMin))
                        MiniStat("平均", if (ping.samples.isEmpty()) "--" else String.format(Locale.US, "%.0f ms", ping.rttAvg))
                        MiniStat("最大", if (ping.samples.isEmpty()) "--" else String.format(Locale.US, "%.0f ms", ping.rttMax))
                        MiniStat("DNS", state.dnsMs?.let { String.format(Locale.US, "%.0f ms", it) } ?: "--")
                    }

                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().height(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (state.pingRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.primary
                            )
                            .clickable(enabled = !state.pingRunning) { onRunDiagnostics() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (state.pingRunning) "正在测量…" else if (state.networkVisible) "手动重测" else "重新测量",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ping 使用系统 ping 命令发起真实 ICMP；DNS 延迟是直接向 DNS 服务器查询的往返时间，不受系统缓存影响。网络页每 2 秒自动刷新。",
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }

            // ── 智能诊断 ────────────────────────────────
            Spacer(Modifier.height(14.dp))
            SectionTitle("智能诊断", trailing = "基于真实测量")
            Spacer(Modifier.height(9.dp))
            val diagnosis = remember(
                state.ping, state.wifi, state.dnsProbes, state.publicIpv6, state.accessType
            ) {
                com.flowstats.app.data.DiagnosisEngine.analyze(
                    ping = state.ping,
                    wifi = state.wifi,
                    dnsProbes = state.dnsProbes,
                    hasIpv6 = !state.publicIpv6.isNullOrBlank(),
                    isMobile = state.accessType != "Wi-Fi" && !state.wifi.isWifi
                )
            }
            DiagnosisCard(diagnosis)

            // ── 链路质量历史 ────────────────────────────
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("链路质量历史", modifier = Modifier.weight(1f))
                if (state.linkHistory.isNotEmpty()) {
                    Text(
                        "清空",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onClearLinkHistory() }.padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            LinkHistoryCard(state.linkHistory)

            // ── 运行日志（事件时间轴）────────────────────
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("运行日志", modifier = Modifier.weight(1f))
                if (state.events.isNotEmpty()) {
                    Text(
                        "${state.events.size} 条",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        "导出",
                        fontSize = 10.sp,
                        color = Blue,
                        modifier = Modifier.clickable { onExportLogs() }.padding(4.dp)
                    )
                    Text(
                        "全部 ›",
                        fontSize = 10.sp,
                        color = Blue,
                        modifier = Modifier.clickable { logViewerOpen = true }.padding(start = 6.dp).padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                corner = 22.dp,
                elevation = 3.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (state.events.isEmpty()) {
                        Text(
                            "暂无事件记录。断网、恢复、制式切换、AP 切换与信号突变会自动记录在这里。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        state.events.take(14).forEachIndexed { index, event ->
                            EventRow(event, fmt)
                            if (index != state.events.take(14).lastIndex) RowDivider()
                        }
                        if (state.events.size > 14) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "另有 ${state.events.size - 14} 条较早记录",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
    if (logViewerOpen) {
        LogViewerDialog(
            events = state.events,
            onDismiss = { logViewerOpen = false },
            onClear = { onClearEvents() },
            onExport = { onExportLogs() }
        )
    }
    }
}

/** 全屏运行日志查看页：条数/占用统计、导出、清空、完整日志列表 */
@Composable
private fun LogViewerDialog(
    events: List<NetEvent>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // 顶栏
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("‹", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("运行日志", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("最近 ${events.size} 条真实记录", fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("清空", fontSize = 11.5.sp, color = Danger,
                    modifier = Modifier.clickable { onClear() }.padding(6.dp))
            }
            // 导出按钮
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onExport() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⇩", fontSize = 15.sp, color = Blue)
                    Spacer(Modifier.width(7.dp))
                    Text("导出日志", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Blue)
                }
            }
            Spacer(Modifier.height(12.dp))
            // 统计条
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("全部日志", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                val bytes = com.flowstats.app.data.NetEventLog.estimatedBytes(events)
                Text("${events.size} 条 · 占用 ${if (bytes < 1024) "${bytes}B" else "%.1fKB".format(bytes / 1024.0)}",
                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            // 列表
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
                    corner = 20.dp, elevation = 2.dp) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        if (events.isEmpty()) {
                            Text("暂无日志。断网、恢复、诊断、DNS、导出等真实动作会自动记录在这里。",
                                fontSize = 11.sp, lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp))
                        } else {
                            events.forEachIndexed { i, e ->
                                EventRow(e, remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) })
                                if (i != events.lastIndex) RowDivider()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun stabilityLabel(score: Int, empty: Boolean): String = when {
    empty -> "尚未测量"
    score >= 90 -> "网络优秀"
    score >= 80 -> "网络良好"
    score >= 60 -> "网络一般"
    else -> "网络较差"
}

@Composable
private fun ConnectionCard(
    state: UiState,
    wifi: WifiDetail,
    onOpenLocationSettings: () -> Unit
) {
    SoftCard(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        corner = 22.dp,
        elevation = 3.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        wifi.ssid ?: if (wifi.isWifi) "Wi-Fi" else (state.accessType ?: "未连接"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(5.dp).clip(RoundedCornerShape(50))
                                .background(if (state.accessType != null) Success else Danger)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when {
                                state.accessType == null -> "当前离线"
                                wifi.isWifi -> "Wi-Fi 已连接"
                                else -> "${state.accessType} 已连接"
                            },
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (wifi.rssi != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        SignalBars(
                            wifi.signalLevel,
                            color = when {
                                wifi.rssi >= -60 -> Success
                                wifi.rssi >= -72 -> Orange
                                else -> Danger
                            }
                        )
                        Text(
                            "${wifi.rssi} dBm",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            // SSID 读不到通常是因为 Android 10+ 需要定位权限
            if (wifi.isWifi && wifi.ssid == null) {
                Spacer(Modifier.height(11.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Orange.copy(alpha = 0.12f))
                        .clickable { onOpenLocationSettings() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "无法读取 Wi-Fi 名称与信号",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Android 10 起需授予定位权限，点此前往设置",
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text("›", fontSize = 17.sp, color = Orange)
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: NetEvent, fmt: SimpleDateFormat) {
    val (color, label) = when (event.level) {
        com.flowstats.app.data.EventLevel.SUCCESS -> Success to "成功"
        com.flowstats.app.data.EventLevel.ERROR -> Danger to "错误"
        com.flowstats.app.data.EventLevel.WARN -> Orange to "告警"
        com.flowstats.app.data.EventLevel.STAT -> Blue to "统计"
        com.flowstats.app.data.EventLevel.INFO -> Slate to "信息"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            fmt.format(Date(event.time)),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Box(
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.13f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            event.detail,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

/**
 * 公网 IP 行：区分「获取中 / 失败 / 已获取」三种状态。
 * 之前只有一个「获取中…」的占位，接口打不通时永远停在那里，看不出是失败。
 */
@Composable
private fun PublicIpRow(state: UiState, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "公网 IP",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            when {
                state.publicIpLoading -> Text(
                    "获取中…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                state.publicIpFailed -> Row(
                    Modifier.clickable { onRetry() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("获取失败", fontSize = 11.sp, color = Orange)
                    Text(
                        " · 重试",
                        fontSize = 11.sp,
                        color = Blue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                state.publicIp == null && state.publicIpv6 == null -> Text(
                    "点按获取",
                    fontSize = 11.sp,
                    color = Blue,
                    modifier = Modifier.clickable { onRetry() }
                )
                else -> Text(
                    "刷新",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onRetry() }.padding(horizontal = 2.dp, vertical = 2.dp)
                )
            }
        }
        state.publicIp?.let {
            Text(
                "IPv4  $it",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        state.publicIpv6?.let {
            Text(
                "IPv6  $it",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 链路质量历史卡片。
 * 每条颜色由协商速率分级，另列 RSSI 与 ICMP 延迟，均为真实采样值。
 */
@Composable
private fun LinkHistoryCard(samples: List<com.flowstats.app.data.LinkQualitySample>) {
    SoftCard(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        corner = 22.dp,
        elevation = 3.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (samples.isEmpty()) {
                Text(
                    "暂无历史。前台每轮测量（约 2 秒）会自动记录一条协商速率、信号与延迟，保存在本机。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                // 当前（最新一条）汇总
                val latest = samples.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(gradeColor(latest.grade))
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "当前 ${latest.grade.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = gradeColor(latest.grade)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "共 ${samples.size} 条记录",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                val shown = samples.take(12)
                shown.forEachIndexed { index, s ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmt.format(Date(s.time)),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(66.dp)
                        )
                        Box(
                            Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(gradeColor(s.grade))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            s.linkMbps?.let { "$it Mbps" } ?: (if (s.isWifi) "—" else "移动网络"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        s.rssi?.let {
                            Text(
                                "$it dBm",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(56.dp)
                            )
                        }
                        s.latencyMs?.let {
                            Text(
                                "${it.toInt()} ms",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(52.dp)
                            )
                        }
                    }
                    if (index != shown.lastIndex) RowDivider()
                }
                if (samples.size > 12) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "另有 ${samples.size - 12} 条较早记录",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "颜色按协商速率分级，仅反映 Wi-Fi 链路能力，不代表实际下载速度。",
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun gradeColor(grade: com.flowstats.app.data.LinkGrade): Color = when (grade) {
    com.flowstats.app.data.LinkGrade.EXCELLENT -> Success   // 绿
    com.flowstats.app.data.LinkGrade.GOOD -> Teal            // 青
    com.flowstats.app.data.LinkGrade.FAIR -> Warning         // 黄
    com.flowstats.app.data.LinkGrade.POOR -> Orange          // 橙
    com.flowstats.app.data.LinkGrade.BAD -> Danger           // 红
    com.flowstats.app.data.LinkGrade.UNKNOWN -> Slate
}

/** 智能诊断卡片：结论横幅 + 指标条形图 + 建议列表，全部基于真实测量 */
@Composable
private fun DiagnosisCard(report: com.flowstats.app.data.DiagnosisEngine.Report) {
    val levelColor: Color = when (report.overall) {
        com.flowstats.app.data.DiagnosisEngine.Level.EXCELLENT -> Success
        com.flowstats.app.data.DiagnosisEngine.Level.GOOD -> Teal
        com.flowstats.app.data.DiagnosisEngine.Level.FAIR -> Warning
        com.flowstats.app.data.DiagnosisEngine.Level.POOR -> Orange
        com.flowstats.app.data.DiagnosisEngine.Level.BAD -> Danger
        else -> Slate
    }
    SoftCard(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        corner = 22.dp,
        elevation = 3.dp
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            // 结论横幅
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(levelColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "综合评估 · ${report.overall.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = levelColor
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                report.headline,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 指标条形图
            if (report.metrics.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                report.metrics.forEach { m ->
                    val c: Color = when (m.level) {
                        com.flowstats.app.data.DiagnosisEngine.Level.EXCELLENT -> Success
                        com.flowstats.app.data.DiagnosisEngine.Level.GOOD -> Teal
                        com.flowstats.app.data.DiagnosisEngine.Level.FAIR -> Warning
                        com.flowstats.app.data.DiagnosisEngine.Level.POOR -> Orange
                        com.flowstats.app.data.DiagnosisEngine.Level.BAD -> Danger
                        else -> Slate
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.label,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(60.dp)
                        )
                        Box(
                            Modifier.weight(1f).height(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                        ) {
                            m.ratio?.let { r ->
                                Box(
                                    Modifier.fillMaxWidth(r.coerceIn(0.02f, 1f)).height(7.dp)
                                        .clip(RoundedCornerShape(4.dp)).background(c)
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            m.value,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (m.level == com.flowstats.app.data.DiagnosisEngine.Level.UNKNOWN)
                                MaterialTheme.colorScheme.onSurfaceVariant else c,
                            modifier = Modifier.width(72.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 建议列表
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)))
            Spacer(Modifier.height(10.dp))
            Text("诊断建议", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            report.suggestions.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        if (s.warning) "⚠" else "•",
                        fontSize = 11.sp,
                        color = if (s.warning) Orange else Blue
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.text,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "结论由真实采样值按阈值规则生成；协商速率仅反映链路能力，不代表实测网速。",
                fontSize = 9.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
