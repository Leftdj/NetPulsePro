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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.Totals
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Teal
import java.util.Locale
import kotlin.math.abs

@Composable
fun InsightsScreen(
    state: UiState,
    onMenu: () -> Unit,
    onOpenApp: (com.flowstats.app.data.AppUsage) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("流量", onMenu = onMenu, onAction = onOpenSettings)
        Column(Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 96.dp)) {
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("每日流量趋势", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("近 7 天", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (state.trendReady && state.dailyTrend.isNotEmpty()) {
                        TrendBars(state.dailyTrend)
                    } else {
                        ChartPlaceholder(110.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("━ 移动", color = Orange, fontSize = 10.sp)
                        Text("━ WiFi", color = Teal, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("网络占比", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(108.dp)) {
                            val total = state.summary.total.coerceAtLeast(1)
                            val mr = state.summary.mobile.toFloat() / total
                            val wr = state.summary.wifi.toFloat() / total
                            RingChart(mr, Orange, Modifier.fillMaxSize(), strokeWidth = 12f)
                            RingChart(wr, Teal, Modifier.fillMaxSize(), strokeWidth = 12f,
                                startAngle = -90f + mr * 360f, trackColor = Color.Transparent)
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(mr * 100).toInt()}%", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text("移动", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            LegendRow("移动网络", formatBytes(state.summary.mobile), Orange)
                            LegendRow("WiFi 网络", formatBytes(state.summary.wifi), Teal)
                            Text("合计 ${formatBytes(state.summary.total)}", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("时段分布", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    if (state.hourlyReady) {
                        HeatMap(state.hourly)
                    } else {
                        ChartPlaceholder(40.dp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00:00", "06:00", "12:00", "18:00", "24:00").forEach {
                            Text(it, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("应用排行 TOP 5", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    val top = state.apps.filter { (!it.isSystem || state.showSystem) && !(state.hideUnknown && it.isUnknown) }
                        .sortedByDescending { it.total }.take(5)
                    if (top.isEmpty()) {
                        Text("暂无数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp))
                    }
                    top.forEachIndexed { i, app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenApp(app) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp))
                                    .background(Brush.linearGradient(rankColors(i))),
                                contentAlignment = Alignment.Center
                            ) { Text("${i + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(9.dp))
                            AppIcon(app.icon, app.label, 26.dp, unknown = app.isUnknown)
                            Spacer(Modifier.width(9.dp))
                            Text(app.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatBytes(app.total), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 排行序号配色：前三名金银铜，4、5 名用品牌青紫，保证每位颜色都不同、一眼能区分 */
private fun rankColors(index: Int): List<Color> = when (index) {
    0 -> listOf(Color(0xFFFFC24B), Color(0xFFF59E0B))
    1 -> listOf(Color(0xFFB9C6D8), Color(0xFF7C8DA6))
    2 -> listOf(Color(0xFFE8A87C), Color(0xFFC2703C))
    3 -> listOf(Color(0xFF35D6C6), Color(0xFF0E9F8E))
    else -> listOf(Color(0xFF8B93F8), Color(0xFF5A5BD6))
}

@Composable
private fun ChartPlaceholder(height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) { Text("正在读取流量数据…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun TrendBars(data: List<Pair<Totals, String>>) {
    val maxValue = data.maxOfOrNull { it.first.total }?.coerceAtLeast(1L) ?: 1L
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom) {
        data.forEach { (totals, label) ->
            val mh by animateDpAsState((totals.mobile.toFloat() / maxValue * 78f).dp,
                tween(650), label = "trendM")
            val wh by animateDpAsState((totals.wifi.toFloat() / maxValue * 78f).dp,
                tween(650), label = "trendW")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(verticalArrangement = Arrangement.Bottom, modifier = Modifier.height(86.dp)) {
                    Box(Modifier.width(16.dp).height(wh).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF2AD4C4), Teal))))
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.width(16.dp).height(mh).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFB457), Orange))))
                }
                Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun HeatMap(data: List<Pair<Long, Long>>) {
    val maxValue = data.maxOfOrNull { it.first + it.second }?.coerceAtLeast(1L) ?: 1L
    var selected by remember { mutableStateOf<Int?>(null) }
    Column {
        val hint = selected?.let { i ->
            val item = data.getOrNull(i)
            if (item == null) null else String.format(
                Locale.getDefault(), "%02d:00-%02d:00 · 移动 %s · WiFi %s",
                i, i + 1, formatBytes(item.first), formatBytes(item.second)
            )
        } ?: "点按柱条查看具体时段"
        Text(hint, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { i, pair ->
                val (mobile, wifi) = pair
                val ratio = (mobile + wifi).toFloat() / maxValue
                val h by animateDpAsState((4f + ratio * 30f).dp, tween(500), label = "heat")
                val isSelected = selected == i
                Box(
                    Modifier.weight(1f).height(h)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF3769F5).copy(alpha = if (isSelected) 1f else 0.9f),
                                    Color(0xFF7B4DFF).copy(alpha = if (isSelected) 0.9f else 0.35f + ratio * 0.5f)
                                )
                            )
                        )
                        .clickable { selected = if (isSelected) null else i }
                )
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
