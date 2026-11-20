package com.flowstats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowstats.app.data.DeviceMeta
import com.flowstats.app.data.LanDevice
import com.flowstats.app.data.PingReport
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 局域网设备详情：基本信息 / 在线状态 / 网络质量（真实 ping）/ 端口 / 设备记录 / 用户管理。
 * 备注、收藏、忽略由调用方持久化；所有数值来自扫描结果或当场实测，无估算。
 */
@Composable
fun LanDeviceDetailDialog(
    device: LanDevice,
    meta: DeviceMeta,
    onDismiss: () -> Unit,
    onMetaChange: (DeviceMeta) -> Unit,
    onPing: ((PingReport) -> Unit) -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) }
    var noteInput by remember { mutableStateOf(meta.note) }
    var pingReport by remember { mutableStateOf<PingReport?>(null) }
    var pinging by remember { mutableStateOf(false) }

    // 打开即测一次真实延迟
    LaunchedEffect(device.ip) {
        pinging = true
        onPing { report -> pingReport = report; pinging = false }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth(0.94f).heightIn(max = 620.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("设备详情", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(meta.note.takeIf { it.isNotBlank() } ?: device.hostname ?: device.deviceType,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("关闭", fontSize = 12.sp, color = Blue,
                    modifier = Modifier.clickable { onDismiss() }.padding(4.dp))
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("基本信息")
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), corner = 14.dp) {
                Column(Modifier.padding(12.dp)) {
                    DetailRow("IP 地址", device.ip)
                    DetailRow("MAC 地址", device.mac ?: "不可见")
                    DetailRow("厂商", device.vendor ?: "未知")
                    DetailRow("主机名", device.hostname ?: "未获取")
                    DetailRow("设备类型", device.deviceType)
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("在线状态")
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), corner = 14.dp) {
                Column(Modifier.padding(12.dp)) {
                    DetailRow("当前状态", if (device.respondedNow) "有响应" else "仅历史记录",
                        valueColor = if (device.respondedNow) Success else MaterialTheme.colorScheme.onSurfaceVariant)
                    DetailRow("发现方式", device.sources.joinToString(" / ") { "${it.label}（${it.confidence}）" })
                    DetailRow("最近发现", fmt.format(Date(device.lastSeen)))
                    DetailRow("首次发现", fmt.format(Date(device.firstSeen)))
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("网络质量")
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), corner = 14.dp) {
                Column(Modifier.padding(12.dp)) {
                    DetailRow("ICMP 延迟", device.responseMs?.let { "${it.toInt()} ms" } ?: "扫描时未测到")
                    when {
                        pinging -> DetailRow("当场实测", "测量中…", valueColor = Orange)
                        pingReport?.error != null -> DetailRow("当场实测", pingReport!!.error!!, valueColor = Danger)
                        pingReport != null -> {
                            DetailRow("当场实测 RTT", "${pingReport!!.rttAvg.toInt()} ms " +
                                "(最小 ${pingReport!!.rttMin.toInt()} / 最大 ${pingReport!!.rttMax.toInt()})",
                                valueColor = Success)
                            DetailRow("丢包率", "${pingReport!!.lossPercent}%",
                                valueColor = if (pingReport!!.lossPercent > 0) Orange else Success)
                            DetailRow("抖动", "${pingReport!!.jitter.toInt()} ms")
                        }
                        else -> DetailRow("当场实测", "未测量")
                    }
                    Text("当场实测为对 ${device.ip} 发送 3 个 ICMP 包的真实结果。",
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("端口信息")
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), corner = 14.dp) {
                Column(Modifier.padding(12.dp)) {
                    DetailRow("探测端口", "22 / 80 / 443 / 8080")
                    DetailRow("有响应", device.openPorts.joinToString(" / ").ifBlank { "无" })
                    device.portProbeMs?.let { DetailRow("探测耗时", "$it ms") }
                        ?: DetailRow("探测耗时", "未探测（设备数过多跳过）")
                    Text("端口有响应不代表服务存在安全漏洞。",
                        fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionTitle("用户管理")
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), corner = 14.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text("设备备注", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    BasicTextField(
                        value = noteInput,
                        onValueChange = { if (it.length <= 16) noteInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            if (noteInput.isEmpty()) {
                                Text("给这台设备起个名字", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                            inner()
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("保存备注", fontSize = 11.sp, color = Blue, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            onMetaChange(meta.copy(note = noteInput.trim()))
                        }.padding(vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ToggleChip(if (meta.favorite) "已收藏" else "收藏", meta.favorite, Blue) {
                            onMetaChange(meta.copy(favorite = !meta.favorite))
                        }
                        Spacer(Modifier.width(8.dp))
                        ToggleChip(if (meta.ignored) "已忽略" else "忽略", meta.ignored, Orange) {
                            onMetaChange(meta.copy(ignored = !meta.ignored))
                        }
                    }
                    if (meta.ignored) {
                        Spacer(Modifier.height(4.dp))
                        Text("忽略后该设备不显示在列表与拓扑中，可在此取消。",
                            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(Blue))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(78.dp))
        Text(value, fontSize = 10.5.sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ToggleChip(label: String, active: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp))
        .background(if (active) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
    }
}
