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
import com.flowstats.app.data.LanDevice
import com.flowstats.app.data.LanReport
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
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
    onScan: () -> Unit
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
                Text(
                    if (scanning) "扫描中…" else "重新扫描",
                    fontSize = 11.sp,
                    color = Blue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !scanning) { onScan() }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                report?.error != null -> Text(
                    report.error!!,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                report == null -> Text(
                    "点「重新扫描」探测当前局域网在线设备（本机 /24 网段，约需几秒）。" +
                        "会向局域网发送 ICMP 请求，请勿频繁点击。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    Text(
                        "网关 ${report.gateway ?: "未知"} · 本机 ${report.localIp ?: "未知"} · ${report.subnet ?: ""}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    report.message?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, fontSize = 9.sp, lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                    }
                    if (report.devices.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("未探测到在线设备，可能被系统防火墙或路由器隔离。",
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
                        "MAC 与厂商依赖系统 ARP 表和内置 OUI 表；Android 隐私限制下可能显示「不可见」。" +
                            "开放端口只代表该端口有响应，不等于服务可安全访问。",
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
                        .background(if (d.isGateway) Blue else if (d.isNew) Orange else Teal)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d.isGateway) "网关" else d.hostname ?: d.vendor ?: "未知设备",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (d.isNew) {
                            Spacer(Modifier.width(6.dp))
                            Text("新发现", fontSize = 8.5.sp, color = Orange)
                        }
                    }
                    Text(
                        d.ip + " · " + (d.mac ?: "MAC 不可见") + (d.vendor?.let { " · $it" } ?: ""),
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (d.openPorts.isEmpty()) "—" else "端口 ${d.openPorts.joinToString("/")}",
                        fontSize = 8.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "首见 ${fmt.format(Date(d.firstSeen))}",
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
private fun TopologyView(devices: List<LanDevice>) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val shown = devices.take(12)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width * 0.38f, size.height * 0.38f)
        shown.forEachIndexed { i, d ->
            val a = Math.toRadians(i * (360f / shown.size).toDouble() - 90)
            val x = cx + (radius * cos(a)).toFloat()
            val y = cy + (radius * sin(a)).toFloat()
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(cx, cy), end = Offset(x, y), strokeWidth = 1f
            )
            drawCircle(
                color = if (d.isGateway) Blue else if (d.isNew) Orange else Teal,
                center = Offset(x, y), radius = 6f
            )
            drawText(
                measurer, d.ip.substringAfterLast('.'),
                topLeft = Offset(x - 9f, y + 10f),
                style = TextStyle(fontSize = 9.sp, color = color)
            )
        }
        drawCircle(color = Blue, center = Offset(cx, cy), radius = 16f, style = Stroke(2f))
        drawText(
            measurer, "网关",
            topLeft = Offset(cx - 9f, cy - 5f),
            style = TextStyle(fontSize = 10.sp, color = Blue, fontWeight = FontWeight.Medium)
        )
    }
    Text(
        "拓扑仅表示「网关—设备」的二层邻接关系，不代表真实物理布线位置。",
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
