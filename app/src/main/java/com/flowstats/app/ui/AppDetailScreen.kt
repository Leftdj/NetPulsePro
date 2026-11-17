package com.flowstats.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.AppUsage
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.HeroColors
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import kotlin.math.abs

@Composable
fun AppDetailScreen(
    app: AppUsage,
    dateLabel: String,
    dailyTrend: List<Pair<com.flowstats.app.data.Totals, String>> = emptyList(),
    onBack: () -> Unit,
    onAction: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(HeroColors))) {
            Column {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) { Text("‹", color = Color.White, fontSize = 24.sp) }
                    Text("应用详情", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable(onClick = onAction),
                        contentAlignment = Alignment.Center
                    ) { Text("⋮", color = Color.White, fontSize = 18.sp) }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.icon, app.label, 58.dp, unknown = app.isUnknown)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(app.packageName, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Column(Modifier.padding(start = 20.dp, bottom = 14.dp)) {
                    Text("累计流量", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    Text(formatBytes(app.total), color = Color.White,
                        fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.padding(14.dp)) {
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("网络构成", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(100.dp)) {
                            val mRatio = if (app.total > 0) app.mobile.toFloat() / app.total else 0f
                            RingChart(mRatio, Orange, Modifier.fillMaxSize(), strokeWidth = 12f)
                            RingChart(if (app.total > 0) app.wifi.toFloat() / app.total else 0f, Teal,
                                Modifier.fillMaxSize(), strokeWidth = 12f,
                                startAngle = -90f + mRatio * 360f, trackColor = Color.Transparent)
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("${(mRatio * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Legend("移动网络", formatBytes(app.mobile), Orange)
                            Legend("WiFi 网络", formatBytes(app.wifi), Teal)
                            Text(dateLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrafficCard("移动下载", formatBytes(app.mobileRx), Orange, Modifier.weight(1f))
                TrafficCard("移动上传", formatBytes(app.mobileTx), Orange, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TrafficCard("WiFi 下载", formatBytes(app.wifiRx), Teal, Modifier.weight(1f))
                TrafficCard("WiFi 上传", formatBytes(app.wifiTx), Teal, Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("近 7 天设备用量", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    if (dailyTrend.isNotEmpty()) TrendMiniBars(dailyTrend) else Text("暂无数据", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("应用信息", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("包名", app.packageName)
                    InfoRow("UID", app.uid.toString())
                    InfoRow("安装时间", formatTime(app.installed))
                    InfoRow("最近更新", formatTime(app.updated))
                }
            }

            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("权限", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (app.permissions.isEmpty()) "无" else app.permissions.joinToString(", "),
                        fontSize = 11.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Legend(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TrafficCard(label: String, value: String, color: Color, modifier: Modifier) {
    SoftCard(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
            overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}

@Composable
private fun TrendMiniBars(data: List<Pair<com.flowstats.app.data.Totals, String>>) {
    val maxValue = data.maxOfOrNull { it.first.total }?.coerceAtLeast(1L) ?: 1L
    Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom) {
        data.forEach { (totals, label) ->
            val h by animateDpAsState(
                (totals.total.toFloat() / maxValue * 66f).dp,
                tween(700), label = "bar"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(20.dp).height(h.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(Brush.verticalGradient(listOf(Blue, Color(0xFF7B4DFF))))
                )
                Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
