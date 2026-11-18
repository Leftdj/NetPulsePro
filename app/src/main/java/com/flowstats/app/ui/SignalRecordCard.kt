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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import com.flowstats.app.data.LinkQualitySample
import com.flowstats.app.data.SignalSpot
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Teal

/**
 * Wi-Fi 信号测试记录：RSSI 曲线直接用链路质量历史里真实采样的值，
 * 位置快照是那一时段采样的统计结果；样本不足不生成记录。
 */
@Composable
fun SignalRecordCard(
    samples: List<LinkQualitySample>,
    spots: List<SignalSpot>,
    onSave: (String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val rssiList = remember(samples) {
        samples.mapNotNull { it.rssi }.take(48) // 最新在前
    }
    var input by remember { mutableStateOf("") }

    SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, corner = 22.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Wi-Fi 信号记录",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            if (rssiList.isEmpty()) {
                Text(
                    "暂无信号采样。进入网络页后每 2 秒会自动记录一次 RSSI，稍后即可看到曲线。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val ordered = rssiList.reversed()
                val current = rssiList.first()
                val weakest = rssiList.minOrNull() ?: current
                val avg = (rssiList.sum() / rssiList.size.toDouble()).toInt()
                val drops = rssiList.zipWithNext().count { (a, b) -> b - a >= 8 }
                Row(Modifier.fillMaxWidth()) {
                    SpotStat("当前", "$current dBm", Modifier.weight(1f))
                    SpotStat("最弱", "$weakest dBm", Modifier.weight(1f))
                    SpotStat("平均", "$avg dBm", Modifier.weight(1f))
                    SpotStat("突降", "$drops 次", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("最近 ${ordered.size} 次 RSSI 采样", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.Bottom) {
                    ordered.forEach { rssi ->
                        // -100..-40 dBm 映射到 4..36dp，越靠上信号越好
                        val ratio = ((rssi + 100) / 60f).coerceIn(0f, 1f)
                        val h = (4 + ratio * 32).dp
                        val color = if (rssi >= -60) Teal else if (rssi >= -75) Blue else Orange
                        Box(
                            Modifier.weight(1f).padding(horizontal = 0.5.dp).height(h)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(color.copy(alpha = 0.8f))
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("-100", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("-70", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("-40 dBm", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = input,
                    onValueChange = { if (it.length <= 12) input = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text("位置名称，如：客厅 / 公司", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        inner()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "保存",
                    fontSize = 11.sp,
                    color = Blue,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = rssiList.isNotEmpty() && input.isNotBlank()) {
                        onSave(input.trim())
                        input = ""
                    }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "保存的是最近 30 条采样的统计值，不是瞬时读数；样本不足时不会生成记录。",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            if (spots.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("已保存位置（${spots.size}）", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                spots.take(8).forEach { spot ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(spot.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${spot.ssid ?: "未知网络"} · ${spot.count} 条采样 · 最弱 ${spot.minRssi} dBm",
                                fontSize = 8.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            Modifier.width(48.dp).height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier.height(5.dp)
                                    .width((((spot.avgRssi + 100) / 60f).coerceIn(0f, 1f) * 48).dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (spot.avgRssi >= -70) Teal else Orange)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${spot.avgRssi}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
                        Text(
                            "×",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onDelete(spot.time) }.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
