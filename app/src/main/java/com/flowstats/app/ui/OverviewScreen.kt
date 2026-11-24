package com.flowstats.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.HeroColors
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Slate
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import java.util.Locale
import kotlinx.coroutines.delay

private fun mbps(bytesPerSecond: Float): String =
    String.format(Locale.US, "%.2f", bytesPerSecond * 8f / 1_000_000f)

@Composable
fun OverviewScreen(
    state: UiState,
    drawerOpen: Boolean = false,
    onMenu: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenUsageSettings: () -> Unit
) {
    val wifi = state.wifi
    val ping = state.ping
    // 每秒一跳，用于把「上一次测量」显示成相对时间
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowTick = System.currentTimeMillis()
        }
    }
    val elapsed = if (state.lastPingAt <= 0L) -1
    else ((nowTick - state.lastPingAt) / 1000).toInt()
    val freshness = when {
        state.pingRunning -> "测量中…"
        elapsed < 0 -> "等待首轮测量"
        elapsed < 3 -> "刚刚自动更新"
        elapsed < 60 -> "${elapsed} 秒前自动更新"
        else -> "${elapsed / 60} 分钟前更新"
    }
    val quotaBytes = state.monthlyQuotaGb * 1024L * 1024 * 1024
    val quotaRatio = (state.month.total.toFloat() / quotaBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        // ── 实时速度 Hero ─────────────────────────────
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(HeroColors))
        ) {
            Column(
                Modifier.statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(onClick = onMenu),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedMenuIcon(open = drawerOpen)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NetPulse Pro",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                        Text(
                            "专业网络监控与诊断",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    AccessChip(
                        label = state.accessType ?: "离线",
                        signal = wifi.signalLevel,
                        online = state.accessType != null
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    SpeedBlock(
                        arrow = "↓",
                        title = "下载",
                        value = mbps(state.speed.download),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.width(1.dp).height(34.dp)
                            .background(Color.White.copy(alpha = 0.18f))
                    )
                    SpeedBlock(
                        arrow = "↑",
                        title = "上传",
                        value = mbps(state.speed.upload),
                        modifier = Modifier.weight(1f).padding(start = 16.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                SpeedChart(state.history)
                Text(
                    "采样间隔 ${state.refreshHz / 1000f}s · 点按曲线看数值",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {

            // ── 权限未授予提示 ──────────────────────────
            if (state.permissionChecked && !state.permissionGranted) {
                Spacer(Modifier.height(18.dp))
                SoftCard(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenUsageSettings),
                    color = Orange.copy(alpha = 0.13f),
                    corner = 18.dp,
                    elevation = 0.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "开启使用情况访问权限",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "未授权时无法读取各应用的真实流量，今日用量会显示为 0。点此前往系统设置。",
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Text("›", fontSize = 20.sp, color = Orange)
                    }
                }
            }

            // ── 今日流量 ────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("今日流量", trailing = "移动 + Wi-Fi")
            Spacer(Modifier.height(9.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                corner = 22.dp,
                elevation = 3.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatBytes(state.today.total),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "↓ ${formatBytes(state.today.rx)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "↑ ${formatBytes(state.today.tx)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val total = state.today.total.coerceAtLeast(1L)
                    AnimatedSplitBar(
                        mobileRatio = state.today.mobile.toFloat() / total,
                        wifiRatio = state.today.wifi.toFloat() / total,
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendDot("移动", formatBytes(state.today.mobile), Orange)
                        LegendDot("Wi-Fi", formatBytes(state.today.wifi), Teal)
                    }
                }
            }

            // ── 套餐预警 ────────────────────────────────
            if (quotaRatio >= 0.8f) {
                Spacer(Modifier.height(10.dp))
                val warnColor = if (quotaRatio >= 1f) Danger else Orange
                SoftCard(
                    Modifier.fillMaxWidth(),
                    color = warnColor.copy(alpha = 0.12f),
                    corner = 18.dp,
                    elevation = 0.dp
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(warnColor)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "!",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (quotaRatio >= 1f) "本月套餐已超额" else "本月套餐已用 ${(quotaRatio * 100).toInt()}%",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "剩余 ${formatBytes((quotaBytes - state.month.total).coerceAtLeast(0L))} · 套餐 ${state.monthlyQuotaGb} GB",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── 连接质量 ────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("连接质量", trailing = freshness)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricTile(
                    glyph = "⌁",
                    label = "协商速率",
                    value = wifi.linkSpeedMbps?.toString(),
                    unit = "Mbps",
                    accent = Blue,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    glyph = "▮",
                    label = "信号强度",
                    value = wifi.rssi?.let { "$it" },
                    unit = "dBm",
                    accent = signalColor(wifi.rssi),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    glyph = "⇅",
                    label = "制式",
                    value = state.accessType ?: "离线",
                    accent = Purple,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricTile(
                    glyph = "◷",
                    label = "延迟",
                    value = if (ping.ok) String.format(Locale.US, "%.0f", ping.rttAvg) else "--",
                    unit = if (ping.ok) "ms" else null,
                    accent = Success,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    glyph = "≈",
                    label = "抖动",
                    value = if (ping.ok) String.format(Locale.US, "%.1f", ping.jitter) else "--",
                    unit = if (ping.ok) "ms" else null,
                    accent = Orange,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    glyph = "⊘",
                    label = "丢包",
                    value = if (ping.ok) ping.lossPercent.toString() else "--",
                    unit = if (ping.ok) "%" else null,
                    accent = if (ping.lossPercent > 0) Danger else Teal,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── 系统状态 ────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SectionTitle("系统状态")
            Spacer(Modifier.height(9.dp))
            SoftCard(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                corner = 20.dp,
                elevation = 2.dp
            ) {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "内存占用",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (state.memory.available) "${state.memory.usedPercent}%" else "设备不支持",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    AnimatedSplitBar(
                        mobileRatio = state.memory.usedPercent / 100f,
                        wifiRatio = 0f,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        mobileColor = if (state.memory.usedPercent >= 85) Danger else Blue
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (state.memory.available)
                            "可用 ${formatBytes(state.memory.availBytes)} / 共 ${formatBytes(state.memory.totalBytes)}"
                        else "系统未返回内存信息",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    RowDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "整机 CPU 占用在 Android 8 之后无公开接口，此处不提供该指标",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // ── 诊断入口 ────────────────────────────────
            Spacer(Modifier.height(18.dp))
            SoftCard(
                Modifier.fillMaxWidth().clickable(onClick = onOpenNetwork),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                corner = 18.dp,
                elevation = 0.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "网络诊断",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Ping · 抖动 · 丢包 · DNS 延迟 · 事件时间轴",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "›",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(96.dp))
        }
    }
}

private fun signalColor(rssi: Int?): Color = when {
    rssi == null -> Slate
    rssi >= -60 -> Success
    rssi >= -72 -> Orange
    else -> Danger
}

@Composable
private fun SpeedBlock(arrow: String, title: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            "$arrow $title",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 3.dp)) {
            Text(
                value,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                "Mbps",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp)
            )
        }
    }
}

@Composable
private fun AccessChip(label: String, signal: Int?, online: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (online) Success else Danger)
                .padding(3.dp)
        ) {}
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
        if (signal != null) {
            Spacer(Modifier.width(6.dp))
            SignalBars(signal, color = Color.White)
        }
    }
}

@Composable
private fun LegendDot(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(6.dp).height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "$label $value",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 汉堡图标 ☰ 平滑变形为关闭图标 ✕ */
@Composable
private fun AnimatedMenuIcon(open: Boolean) {
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "menuIcon"
    )
    val density = LocalDensity.current
    val gap = with(density) { 4.6.dp.toPx() }
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(16.dp).height(1.8.dp)
                .graphicsLayer {
                    translationY = -gap * (1f - progress)
                    rotationZ = 45f * progress
                }
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White)
        )
        Box(
            Modifier.width(16.dp * (1f - progress)).height(1.8.dp)
                .graphicsLayer { alpha = 1f - progress }
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White)
        )
        Box(
            Modifier.width(16.dp).height(1.8.dp)
                .graphicsLayer {
                    translationY = gap * (1f - progress)
                    rotationZ = -45f * progress
                }
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White)
        )
    }
}
