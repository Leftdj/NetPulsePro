package com.flowstats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.ApInfo
import com.flowstats.app.data.ScanSummary
import com.flowstats.app.data.WifiScanReport
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Teal

private enum class ChannelView(val label: String) { GRAPH("图形"), LIST("列表"), ANALYSIS("分析") }

/**
 * Wi-Fi 信道分析仪卡片。
 * 所有数值来自系统扫描结果；明确不是频谱仪，也不把重叠压力说成实测干扰。
 */
@Composable
fun WifiChannelCard(
    report: WifiScanReport?,
    history: List<ScanSummary>,
    scanning: Boolean,
    onScan: () -> Unit,
    onRequestPermission: () -> Unit = {}
) {
    var view by remember { mutableStateOf(0) }
    SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, corner = 22.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Wi-Fi 信道分析",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (scanning) "扫描中…" else "扫描",
                    fontSize = 11.sp,
                    color = Blue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !scanning) { onScan() }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                report?.error != null -> {
                    Text(
                        report.error!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    if (report.error!!.contains("权限")) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "授予权限",
                            fontSize = 11.sp,
                            color = Blue,
                            modifier = Modifier.clickable { onRequestPermission() }.padding(vertical = 2.dp)
                        )
                    }
                }

                report == null -> Text(
                    "点右上「扫描」读取周围 AP 的信道与信号强度。数据来自系统 Wi-Fi 扫描，不是射频频谱仪。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    val bands = report.bandCounts
                    Text(
                        buildString {
                            append("扫描到 ${report.aps.size} 个 AP")
                            if (bands.isNotEmpty()) {
                                append(" · ")
                                append(bands.entries.sortedBy { it.key }.joinToString(" / ") { "${it.key} ${it.value}" })
                            }
                            report.currentChannel?.let { append(" · 当前 CH $it") }
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    report.message?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChannelView.entries.forEachIndexed { i, v ->
                            val on = view == i
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(if (on) Blue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { view = i }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    v.label,
                                    fontSize = 10.5.sp,
                                    color = if (on) Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    when (ChannelView.entries[view]) {
                        ChannelView.GRAPH -> ChannelGraph(report)
                        ChannelView.LIST -> ApList(report.aps)
                        ChannelView.ANALYSIS -> ChannelAnalysis(report)
                    }

                    if (history.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        ScanHistorySpark(history)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "这是 Wi-Fi AP 扫描分析，不是专业频谱仪；Android 限制扫描频率，" +
                            "部分机型不开放完整信道宽度与厂商信息。重叠压力仅用于相对比较。",
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGraph(report: WifiScanReport) {
    val list = report.channels
    if (list.isEmpty()) {
        Text("暂无可显示的信道", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("信道拥挤度（柱越高、邻近 AP 越多）", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(78.dp), verticalAlignment = Alignment.Bottom) {
        list.forEach { st ->
            val color = when (st.band) {
                "2.4 GHz" -> Orange
                "5 GHz" -> Blue
                "6 GHz" -> Purple
                else -> Teal
            }
            val h = (8f + st.crowding * 0.62f).coerceAtMost(72f).dp
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (st.channel == report.currentChannel) {
                    Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.height(2.dp))
                } else {
                    Spacer(Modifier.height(6.dp))
                }
                Box(
                    Modifier.width(if (list.size > 16) 6.dp else 10.dp)
                        .height(h)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(color.copy(alpha = if (st.channel == report.currentChannel) 1f else 0.7f))
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    st.channel.toString(),
                    fontSize = 8.sp,
                    color = if (st.channel == report.currentChannel) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "橙 = 2.4 GHz　蓝 = 5 GHz　紫 = 6 GHz　小圆点 = 当前信道",
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ApList(aps: List<ApInfo>) {
    val shown = aps.take(10)
    Column {
        shown.forEachIndexed { i, ap ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (ap.isCurrent) "当前" else "${i + 1}",
                    fontSize = 9.sp,
                    color = if (ap.isCurrent) Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        ap.ssid,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${ap.band} · CH ${ap.channel} · ${ap.widthMhz?.let { "$it MHz" } ?: "宽度未知"}",
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ap.security?.let {
                    Text(it, fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "${ap.rssi} dBm",
                    fontSize = 10.sp,
                    color = if (ap.rssi >= -60) Teal else if (ap.rssi >= -75) Orange else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(54.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        if (aps.size > shown.size) {
            Text(
                "另有 ${aps.size - shown.size} 个 AP（按信号从强到弱取前 10）",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChannelAnalysis(report: WifiScanReport) {
    val current = report.currentChannel?.let { ch -> report.channels.firstOrNull { it.channel == ch } }
    val best = report.recommendedChannel
    Column {
        InfoLine("当前信道", current?.let { "CH ${it.channel}（${it.band}）" } ?: "未识别")
        InfoLine("当前拥挤度", current?.let { "${it.crowding} / 100 · 扫描到 ${it.count} 个 AP" } ?: "无数据")
        InfoLine("最强信号", current?.let { "${it.strongestRssi} dBm" } ?: "无数据")
        InfoLine("推荐信道", best?.let { "CH $it" } ?: "样本不足")
        Spacer(Modifier.height(6.dp))
        val top = report.channels.sortedByDescending { it.overlapScore }.take(3)
        if (top.isNotEmpty()) {
            Text("拥挤信道 Top 3", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            top.forEach {
                Text(
                    "CH ${it.channel} · ${it.band} · ${it.count} 个 AP · 拥挤度 ${it.crowding}",
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "2.4 GHz 只有 1/6/11 三个互不重叠的信道，推荐只在这三个里选；" +
                "5/6 GHz 信道间隔大，按实际重叠情况排序。",
            fontSize = 9.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(74.dp))
        Text(value, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ScanHistorySpark(history: List<ScanSummary>) {
    val list = history.takeLast(16)
    val max = list.maxOfOrNull { it.apCount }?.coerceAtLeast(1) ?: 1
    Text("最近 ${list.size} 次扫描的 AP 数量", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.Bottom) {
        list.forEach {
            val h = (4 + it.apCount * 24f / max).dp
            Box(
                Modifier.weight(1f).padding(horizontal = 1.dp).height(h)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(Blue.copy(alpha = 0.65f))
            )
        }
    }
}
