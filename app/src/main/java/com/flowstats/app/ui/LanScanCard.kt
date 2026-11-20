package com.flowstats.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.DeviceMeta
import com.flowstats.app.data.DiscoverySource
import com.flowstats.app.data.LanDevice
import com.flowstats.app.data.LanReport
import com.flowstats.app.data.ScanStatus
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 局域网设备扫描卡片：列表 / 拓扑两种视图。
 * 两视图共用同一份 report.devices，保证在线状态、名称完全一致。
 */
@Composable
fun LanScanCard(
    report: LanReport?,
    scanning: Boolean,
    progress: Pair<Int, Int> = 0 to 0,
    deviceMeta: Map<String, DeviceMeta> = emptyMap(),
    onScan: () -> Unit,
    onStop: () -> Unit = {},
    onOpenDevice: (LanDevice) -> Unit = {}
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

            // 扫描状态与进度：明确区分扫描中 / 完成 / 中断 / 失败
            if (scanning) {
                val (done, total) = progress
                ScanProgress(done, total)
                Spacer(Modifier.height(4.dp))
                Text("扫描进行中，完成时显示最终结果", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            } else if (report != null && report.error == null) {
                StatusChip(report.scanStatus)
                Spacer(Modifier.height(6.dp))
            }

            when {
                report?.error != null -> Text(
                    report.error!!,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                report == null && !scanning -> Text(
                    "点「重新扫描」探测当前局域网响应设备（本机 /24 网段，约需几秒）。" +
                        "会向局域网发送 ICMP 请求，请勿频繁点击；可随时停止。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                report != null -> {
                    InfoLine("IPv4 网关", report.gatewayV4 ?: "未获取")
                    InfoLine("IPv6 网关", report.gatewayV6 ?: "未获取")
                    InfoLine("本机 IPv4", report.localIp ?: "未获取")
                    InfoLine("子网掩码", report.mask ?: "系统未上报")
                    InfoLine("扫描范围", report.scanRange ?: "未知")
                    Spacer(Modifier.height(6.dp))

                    // 去重口径：唯一设备数单独强调，命中台次不累加
                    ResultSummary(report)
                    Spacer(Modifier.height(6.dp))
                    report.message?.let {
                        Text(it, fontSize = 9.sp, lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    }

                    // 已忽略设备从两视图中一并过滤，使用同一份数据
                    val visible = report.devices.filterNot { deviceMeta[it.ip]?.ignored == true }
                    val responded = visible.filter { it.respondedNow && !it.isSelf }
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
                        if (topology) {
                            TopologyView(visible, onOpenDevice)
                        } else {
                            DeviceList(visible, deviceMeta, onOpenDevice)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "拓扑与列表共用同一份发现结果；在线设备绿色、仅历史命中灰色，网关与本机以图标区分。" +
                            "MAC 与厂商依赖 ARP 表与内置 OUI，Android 隐私限制下可能显示「不可见」。" +
                            "端口有响应不代表服务存在安全漏洞。",
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
private fun StatusChip(status: ScanStatus) {
    val (text, color) = when (status) {
        ScanStatus.COMPLETE -> "扫描完成" to Success
        ScanStatus.RUNNING -> "扫描中（未出最终结果）" to Orange
        ScanStatus.STOPPED -> "扫描已中断（当前为部分结果）" to Orange
        ScanStatus.FAILED -> "扫描失败" to com.flowstats.app.ui.theme.Danger
    }
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, fontSize = 9.5.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResultSummary(report: LanReport) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(Blue.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("局域网扫描结果", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${report.uniqueDeviceCount}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Blue)
                    Spacer(Modifier.width(4.dp))
                    Text("台唯一设备（去重）", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val parts = mutableListOf<String>()
        DiscoverySource.values().filter { it != DiscoverySource.HISTORY && it != DiscoverySource.SELF }
            .forEach { s -> report.sourceCounts[s]?.let { parts.add("${s.label} ${it} 台次") } }
        Text("发现方式命中：" + (if (parts.isNotEmpty()) parts.joinToString(" · ") else "无"),
            fontSize = 9.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("同一设备可被多种方式命中，以上台次不累加。",
            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
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
        Text("$done / $total · $pct%", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun DeviceList(
    devices: List<LanDevice>,
    meta: Map<String, DeviceMeta>,
    onOpen: (LanDevice) -> Unit
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }
    Column {
        devices.take(20).forEach { d ->
            val m = meta[d.ip]
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(d) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 设备图标：网关路由器、本机、在线绿、离线灰
                DeviceIcon(d)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m?.note?.takeIf { it.isNotBlank() } ?: d.hostname ?: d.deviceType,
                            fontSize = 11.sp,
                            color = if (d.respondedNow) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (m?.favorite == true) {
                            Spacer(Modifier.width(5.dp))
                            Text("★", fontSize = 10.sp, color = Orange)
                        }
                        if (d.isNew) {
                            Spacer(Modifier.width(5.dp))
                            Text("新", fontSize = 8.5.sp, color = Orange)
                        }
                    }
                    Text(
                        d.ip + if (d.isGateway) " · 网关" else if (d.isSelf) " · 本机" else "" +
                            (if (d.respondedNow) "" else " · 历史"),
                        fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "MAC " + (d.mac ?: "不可见") + (d.vendor?.let { " · $it" } ?: "") +
                            " · 发现 " + d.sources.joinToString("/") { it.label },
                        fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        d.responseMs?.let { "${it.toInt()} ms" } ?: "延迟未测",
                        fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (d.openPorts.isEmpty()) "端口未探" else d.openPorts.joinToString("/"),
                        fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
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

/** 设备图标：网关用 G、本机用本、在线绿点、离线灰点，拓扑与列表共用语义 */
@Composable
private fun DeviceIcon(d: LanDevice) {
    val (label, color) = when {
        d.isGateway -> "G" to Blue
        d.isSelf -> "本" to Teal
        d.respondedNow -> "·" to Success
        else -> "·" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
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
private fun TopologyView(devices: List<LanDevice>, onOpen: (LanDevice) -> Unit) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = devices.take(10)
    val canvasColor = MaterialTheme.colorScheme.onSurface
    // 记录每个节点的像素坐标，供点击命中检测；拓扑与列表用同一份 shown
    val nodePositions = remember { mutableListOf<Pair<Float, Float>>() }
    Box(Modifier.fillMaxWidth().height(210.dp)
        .pointerInput(shown) {
            detectTapGestures { tap ->
                val hit = nodePositions.indexOfFirst { (x, y) ->
                    kotlin.math.hypot(tap.x - x, tap.y - y) < 26f
                }
                if (hit in shown.indices) onOpen(shown[hit])
            }
        }) {
        Canvas(Modifier.fillMaxWidth().height(210.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(size.width * 0.36f, size.height * 0.36f)
            nodePositions.clear()
            shown.forEachIndexed { i, d ->
                val a = Math.toRadians(i * (360f / shown.size).toDouble() - 90)
                val x = cx + (radius * cos(a)).toFloat()
                val y = cy + (radius * sin(a)).toFloat()
                nodePositions.add(x to y)
                drawLine(color.copy(alpha = 0.3f), Offset(cx, cy), Offset(x, y), strokeWidth = 1f)
                val node = when {
                    d.isGateway -> Blue
                    d.isSelf -> Teal
                    !d.respondedNow -> color.copy(alpha = 0.5f)
                    else -> Success
                }
                drawCircle(color = node, center = Offset(x, y), radius = 5f)
                // 节点只标 IP 末段：避免图形拥挤，完整信息点击节点看详情
                val last = d.ip.substringAfterLast('.')
                drawText(
                    measurer, last,
                    topLeft = Offset(x - 9f, y + 9f),
                    style = TextStyle(fontSize = 9.sp, color = canvasColor)
                )
            }
            drawCircle(color = Blue, center = Offset(cx, cy), radius = 17f, style = Stroke(2f))
            drawText(
                measurer, "网关",
                topLeft = Offset(cx - 10f, cy - 5f),
                style = TextStyle(fontSize = 9.sp, color = Blue, fontWeight = FontWeight.Medium)
            )
        }
    }
    Text(
        "节点标注为 IP 末段（如 23 = 192.168.10.23），点击节点打开设备详情；" +
            "绿色在线、灰色为仅历史命中，网关与本机以图标区分。逻辑关系示意，非物理拓扑。",
        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
    )
}
