package com.flowstats.app.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.DiscoverySource
import com.flowstats.app.data.LanDevice
import com.flowstats.app.data.LanReport
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** 局域网设备扫描卡片：列表 / 拓扑两种视图 */
@Composable
fun LanScanCard(
    report: LanReport?,
    scanning: Boolean,
    progress: Pair<Int, Int> = 0 to 0,
    onScan: () -> Unit,
    onStop: () -> Unit = {}
) {
    var topology by remember { mutableStateOf(false) }
    SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, corner = 22.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "局域网设备",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (scanning) {
                    Text("停止", fontSize = 11.sp, color = com.flowstats.app.ui.theme.Danger,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onStop() }.padding(4.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (scanning) "扫描中…" else "重新扫描",
                    fontSize = 11.sp,
                    color = Blue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !scanning) { onScan() }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            if (scanning) {
                val (done, total) = progress
                ScanProgress(done, total)
                Spacer(Modifier.height(8.dp))
            }

            when {
                report?.error != null -> Text(
                    report.error!!,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                report == null && !scanning -> Text(
                    "点「重新扫描」探测当前局域网 responding 设备（本机 /24 网段，约需几秒）。" +
                        "会向局域网发送 ICMP 请求，请勿频繁点击；可随时停止。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                report != null -> {
                    // 网关信息拆分：IPv4 / IPv6 / 本机 / 掩码 / 扫描范围，不再混在一行
                    InfoLine("IPv4 网关", report.gatewayV4 ?: "未获取")
                    InfoLine("IPv6 网关", report.gatewayV6 ?: "未获取")
                    InfoLine("本机 IPv4", report.localIp ?: "未获取")
                    InfoLine("子网掩码", report.mask ?: "系统未上报")
                    InfoLine("扫描范围", report.scanRange ?: "未知")
                    report.message?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, fontSize = 9.sp, lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    }
                    if (report.stopped) {
                        Spacer(Modifier.height(3.dp))
                        Text("本次扫描已手动停止，以上为停止前已获取的结果。",
                            fontSize = 9.sp, color = Orange)
                    }
                    val responded = report.devices.filter { it.respondedNow && !it.isSelf }
                    if (responded.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("未探测到响应设备，可能被系统防火墙或路由器隔离。请注意 ICMP 不响应不等于设备离线。",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(false to "列表", true to "拓扑").forEach { (v, label) ->
                                val on = topology == v
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (on) Blue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { topology = v }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(label, fontSize = 10.5.sp,
                                        color = if (on) Blue else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (topology) TopologyView(report.devices) else DeviceList(report.devices)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "发现方式：ICMP 响应 / ARP 表 / TCP 端口响应 / 网关配置 / 本机，另有仅命中历史记录的设备（低可信，不计入当前响应）。" +
                            "MAC 与厂商依赖系统 ARP 表和内置 OUI 表，Android 隐私限制下可能显示「不可见」。" +
                            "设备类型仅依主机名或厂商粗略推测；端口有响应不代表服务存在安全漏洞。",
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
private fun ScanProgress(done: Int, total: Int) {
    val pct = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(5.dp)
                .clip(RoundedCornerShape(3.dp)).background(Blue))
        }
        Spacer(Modifier.width(8.dp))
        Text("$done / $total", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(66.dp))
        Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DeviceList(devices: List<LanDevice>) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    Column {
        devices.take(20).forEach { d ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(nodeColor(d))
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.ip + if (d.isGateway) "（网关）" else if (d.isSelf) "（本机）" else "",
                            fontSize = 11.sp,
                            color = if (d.respondedNow) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (d.isNew) {
                            Spacer(Modifier.width(6.dp))
                            Text("新发现", fontSize = 8.5.sp, color = Orange)
                        }
                    }
                    Text(
                        listOfNotNull(d.hostname, d.deviceType).joinToString(" · "),
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "发现：" + d.sources.joinToString(" / ") { it.label + "（${it.confidence}）" } +
                            " · " + (d.mac ?: "MAC 不可见") + (d.vendor?.let { " · $it" } ?: ""),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (d.openPorts.isEmpty()) "端口未探" else d.openPorts.joinToString(" / "),
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "最近 ${fmt.format(Date(d.lastSeen))}",
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (devices.size > 20) {
            Text("另有 ${devices.size - 20} 台设备未显示", fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun nodeColor(d: LanDevice): Color = when {
    d.isGateway -> Blue
    d.isSelf -> Teal
    !d.respondedNow -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    d.isNew -> Orange
    else -> Success
}

@Composable
private fun TopologyView(devices: List<LanDevice>) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = devices.take(10)
    val gateway = devices.firstOrNull { it.isGateway }
    val labelColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(210.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width * 0.36f, size.height * 0.36f)
        shown.forEachIndexed { i, d ->
            val a = Math.toRadians(i * (360f / shown.size).toDouble() - 90)
            val x = cx + (radius * cos(a)).toFloat()
            val y = cy + (radius * sin(a)).toFloat()
            drawLine(
                color = color.copy(alpha = 0.3f),
                start = Offset(cx, cy), end = Offset(x, y), strokeWidth = 1f
            )
            val node = when {
                d.isGateway -> Blue
                d.isSelf -> Teal
                !d.respondedNow -> color.copy(alpha = 0.5f)
                else -> Success
            }
            drawCircle(color = node, center = Offset(x, y), radius = 5f)
            // 节点标签：IP 末段（不是端口）+ 名称/在线状态
            val last = d.ip.substringAfterLast('.')
            val name = d.hostname ?: if (d.isGateway) "网关" else if (d.isSelf) "本机" else d.deviceType
            val status = if (d.respondedNow) "在线" else "历史"
            val text = "$last · ${name.take(8)} · $status"
            drawText(
                measurer, text,
                topLeft = Offset(x - 34f, y + 8f),
                style = TextStyle(fontSize = 7.5.sp, color = labelColor)
            )
        }
        drawCircle(color = Blue, center = Offset(cx, cy), radius = 17f, style = Stroke(2f))
        drawText(
            measurer, gateway?.ip?.substringBeforeLast('.') ?: "网关",
            topLeft = Offset(cx - 26f, cy - 6f),
            style = TextStyle(fontSize = 8.sp, color = Blue, fontWeight = FontWeight.Medium)
        )
    }
    Text(
        "节点标注为「IP 末段 · 名称 · 在线状态」，不是端口号；仅表示「网关—设备」的逻辑关系，" +
            "不代表真实物理拓扑或布线位置。灰色为仅历史记录命中。",
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionTrailing(text: String) {
    Text(text, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
