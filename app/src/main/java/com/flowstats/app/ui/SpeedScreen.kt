package com.flowstats.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.SpeedSample
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.HeroColors
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TopBar(
    title: String,
    transparent: Boolean = false,
    onMenu: () -> Unit,
    onAction: () -> Unit
) {
    val tint = if (transparent) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (transparent) Color.Transparent else MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(if (transparent) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) { Text("☰", color = tint, fontSize = 18.sp) }
        Text(
            title, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        )
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(if (transparent) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center
        ) { Text("⚙", color = tint, fontSize = 17.sp) }
    }
}

@Composable
fun PermissionBanner(onOpenSettings: () -> Unit) {
    SoftCard(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        color = Color(0xFFFFF3E0)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("需要开启使用权限", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A4B00))
            Spacer(Modifier.height(6.dp))
            Text(
                "授予「使用情况访问权限」后，才能按应用分别读取移动网络与 WiFi 的下载 / 上传流量。",
                fontSize = 12.sp, color = Color(0xFF8A4B00)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Orange)
                    .clickable(onClick = onOpenSettings).padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("前往授权", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun SpeedScreen(
    state: UiState,
    onMenu: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(HeroColors))) {
            Column {
                TopBar("网络速度", transparent = true, onMenu = onMenu, onAction = onOpenSettings)
                if (state.permissionChecked && !state.permissionGranted) PermissionBanner(onOpenSettings)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulseDot()
                    Spacer(Modifier.width(8.dp))
                    Text("实时网络流量", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, top = 10.dp, end = 20.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        String.format(java.util.Locale.US, "%.2f", state.speed.download * 8f / 1_000_000f),
                        fontSize = 46.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp, color = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Mbps", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Column(Modifier.weight(1f).padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
                        Text("↓ ${formatSpeed(state.speed.download)}", color = Color.White, fontSize = 11.sp)
                        Text("↑ ${formatSpeed(state.speed.upload)}", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                    }
                }
                SpeedChart(state.history)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("━ 下载", color = Color(0xFFB9F0FF), fontSize = 10.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("━ 上传", color = Color(0xFFFFCA6B), fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text("每秒刷新 · 点按曲线看数值", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }

        Column(Modifier.padding(14.dp)) {
            val peak = state.history.maxOfOrNull { it.download } ?: 0f
            val avg = if (state.history.isEmpty()) 0f else state.history.map { it.download }.average().toFloat()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("峰值速度", String.format(java.util.Locale.US, "%.2f", peak * 8f / 1_000_000f), "Mbps", Blue, Modifier.weight(1f))
                StatCard("平均速度", String.format(java.util.Locale.US, "%.2f", avg * 8f / 1_000_000f), "Mbps", Purple, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("今日流量", formatBytes(state.today.total), "", Teal, Modifier.weight(1f))
                StatCard("本月流量", formatBytes(state.month.total), "", Orange, Modifier.weight(1f))
            }

            Text("网络通道", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            ChannelCard("移动数据", formatBytes(state.today.mobile), state.today.mobile.toFloat() / state.today.total.coerceAtLeast(1), Orange)
            Spacer(Modifier.height(9.dp))
            ChannelCard("WiFi 网络", formatBytes(state.today.wifi), state.today.wifi.toFloat() / state.today.total.coerceAtLeast(1), Teal)

            val quotaBytes = state.monthlyQuotaGb * 1024L * 1024 * 1024
            val quotaRatio = (state.month.total.toFloat() / quotaBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
            val quotaColor = when {
                quotaRatio >= 1f -> Danger
                quotaRatio >= 0.8f -> Orange
                else -> Blue
            }
            if (quotaRatio >= 0.8f) {
                Spacer(Modifier.height(14.dp))
                SoftCard(Modifier.fillMaxWidth(), color = quotaColor.copy(alpha = 0.12f), elevation = 0.dp) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(quotaColor),
                            contentAlignment = Alignment.Center
                        ) { Text("!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (quotaRatio >= 1f) "本月套餐已超额"
                                else "本月套餐已用 ${(quotaRatio * 100).toInt()}%",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "剩余 ${formatBytes((quotaBytes - state.month.total).coerceAtLeast(0L))} · 套餐 ${state.monthlyQuotaGb} GB",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }

            Text("今日用量", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RingStat("移动", "今日占比", state.today.mobile.toFloat() / state.today.total.coerceAtLeast(1), Orange, Modifier.weight(1f))
                RingStat("WiFi", "今日占比", state.today.wifi.toFloat() / state.today.total.coerceAtLeast(1), Teal, Modifier.weight(1f))
                RingStat(
                    "本月套餐", "${formatBytes(state.month.total)} / ${state.monthlyQuotaGb}GB",
                    quotaRatio, quotaColor, Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, accent: Color, modifier: Modifier) {
    SoftCard(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (unit.isNotEmpty()) Text(unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun ChannelCard(label: String, value: String, ratio: Float, color: Color) {
    SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(color), contentAlignment = Alignment.Center) {
                    Text(if (color == Orange) "📶" else "📡", fontSize = 14.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(10.dp))
            AnimatedSplitBar(
                mobileRatio = if (color == Orange) ratio else 0f,
                wifiRatio = if (color == Teal) ratio else 0f,
                modifier = Modifier.fillMaxWidth().height(7.dp)
            )
        }
    }
}

@Composable
private fun RingStat(label: String, sub: String, progress: Float, color: Color, modifier: Modifier) {
    SoftCard(modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(66.dp)) {
                RingChart(progress, color, Modifier.fillMaxSize(), strokeWidth = 9f)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("${(progress * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SpeedChart(history: List<SpeedSample>) {
    val infinite = rememberInfiniteTransition()
    val glow by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "glow")
    var selected by remember { mutableStateOf<Int?>(null) }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Box(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(130.dp)
                .pointerInput(history.size) {
                    detectTapGestures { offset ->
                        if (history.size > 1) {
                            val idx = (offset.x / size.width * history.lastIndex).roundToInt()
                                .coerceIn(0, history.lastIndex)
                            selected = if (selected == idx) null else idx
                        }
                    }
                }
        ) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val plot = bottom - top
            for (i in 0..3) {
                val y = top + i * plot / 3
                drawLine(Color.White.copy(alpha = 0.16f), Offset(left, y), Offset(right, y), 1f)
            }
            if (history.size > 1) {
                val down = history.map { it.download * 8f / 1_000_000f }
                val up = history.map { it.upload * 8f / 1_000_000f }
                val peak = max(0.012f, (down + up).maxOrNull() ?: 0f) * 1.15f
                fun xAt(i: Int) = left + i * (right - left) / (history.lastIndex)
                fun yAt(v: Float) = bottom - (v / peak).coerceIn(0f, 1f) * plot * 0.92f
                fun buildPath(values: List<Float>) = Path().apply {
                    moveTo(xAt(0), yAt(values[0]))
                    for (i in 1 until values.size) {
                        val px = xAt(i - 1); val py = yAt(values[i - 1])
                        val cx = xAt(i); val cy = yAt(values[i])
                        quadraticBezierTo((px + cx) / 2, py, (px + cx) / 2, (py + cy) / 2)
                    }
                    lineTo(xAt(values.lastIndex), yAt(values.last()))
                }
                fun drawSeries(values: List<Float>, stroke: Color, fillStart: Color) {
                    val line = buildPath(values)
                    val area = Path().apply {
                        addPath(line)
                        lineTo(xAt(values.lastIndex), bottom)
                        lineTo(xAt(0), bottom)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(fillStart, Color.Transparent)))
                    drawPath(line, stroke, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                }
                drawSeries(up, Color(0xFFFFCA6B), Color(0x55FFCA6B))
                drawSeries(down, Color(0xFFB9F0FF), Color(0x66A0E8FF))
                val lx = xAt(down.lastIndex)
                val ly = yAt(down.last())
                drawCircle(Color.White.copy(alpha = 0.18f + glow * 0.22f), radius = (9 + glow * 4).dp.toPx(), center = Offset(lx, ly))
                drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(lx, ly))

                // 点选后的竖向游标与数值点
                selected?.let { idx ->
                    if (idx in history.indices) {
                        val x = xAt(idx)
                        drawLine(Color.White.copy(alpha = 0.55f), Offset(x, top), Offset(x, bottom), 1.4f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                        drawCircle(Color.White, 4.dp.toPx(), Offset(x, yAt(down[idx])))
                        drawCircle(Color(0xFFFFCA6B), 3.5.dp.toPx(), Offset(x, yAt(up[idx])))
                    }
                }
            }
        }

        selected?.let { idx ->
            history.getOrNull(idx)?.let { sample ->
                Column(
                    Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 6.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xCC0B1836))
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(fmt.format(Date(sample.time)), color = Color.White.copy(alpha = 0.75f), fontSize = 9.sp)
                    Text(
                        String.format(Locale.US, "↓ %.2f  ↑ %.2f Mbps",
                            sample.download * 8f / 1_000_000f, sample.upload * 8f / 1_000_000f),
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
