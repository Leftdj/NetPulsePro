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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowstats.app.data.EventLevel
import com.flowstats.app.data.LinkQualitySample
import com.flowstats.app.data.NetEvent
import com.flowstats.app.data.NetEventKind
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class DateFilter(val label: String) { ALL("全部"), TODAY("今天"), YESTERDAY("昨天"), WEEK("近7天") }
private enum class KindFilter(val label: String) { ALL("全部"), NET("网络"), SIGNAL("信号"), DIAG("诊断"), OTHER("其他") }

private fun NetEventKind.group(): KindFilter = when (this) {
    NetEventKind.ONLINE, NetEventKind.OFFLINE, NetEventKind.WIFI, NetEventKind.CELLULAR -> KindFilter.NET
    NetEventKind.SIGNAL -> KindFilter.SIGNAL
    NetEventKind.DIAGNOSTIC, NetEventKind.DNS -> KindFilter.DIAG
    else -> KindFilter.OTHER
}

private fun NetEventKind.title(): String = when (this) {
    NetEventKind.ONLINE -> "网络恢复"
    NetEventKind.OFFLINE -> "网络断开"
    NetEventKind.WIFI -> "Wi-Fi 切换"
    NetEventKind.CELLULAR -> "移动网络"
    NetEventKind.SIGNAL -> "信号异常"
    NetEventKind.DIAGNOSTIC -> "诊断异常"
    NetEventKind.DNS -> "DNS 变化"
    NetEventKind.EXPORT -> "数据导出"
    NetEventKind.IP -> "IP 变化"
}

@Composable
private fun levelColor(level: EventLevel): Color = when (level) {
    EventLevel.SUCCESS -> Success
    EventLevel.WARN -> Warning
    EventLevel.ERROR -> Danger
    EventLevel.STAT -> Blue
    EventLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * 网络故障事件中心：按日期 / 类型筛选，点事件看详情、故障持续时间与前后真实采样。
 * 「前后数据」取链路历史里时间上最接近的采样，不是补算出来的值。
 */
@Composable
fun EventCenterDialog(
    events: List<NetEvent>,
    linkHistory: List<LinkQualitySample>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    var dateFilter by remember { mutableStateOf(DateFilter.ALL) }
    var kindFilter by remember { mutableStateOf(KindFilter.ALL) }
    var selected by remember { mutableStateOf<NetEvent?>(null) }

    val visible = remember(events, dateFilter, kindFilter) {
        events.filter { e ->
            val kindOk = kindFilter == KindFilter.ALL || e.kind.group() == kindFilter
            val dateOk = when (dateFilter) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> isSameDay(e.time, 0)
                DateFilter.YESTERDAY -> isSameDay(e.time, -1)
                DateFilter.WEEK -> System.currentTimeMillis() - e.time <= 7L * 86400_000
            }
            kindOk && dateOk
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("网络事件中心", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text("导出", fontSize = 10.5.sp, color = Blue,
                    modifier = Modifier.clickable { onExport() }.padding(4.dp))
                Text("清空", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onClear() }.padding(4.dp))
                Text("关闭", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onDismiss() }.padding(start = 6.dp).padding(4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("日期", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DateFilter.entries.forEach { f ->
                    FilterChip(f.label, dateFilter == f) { dateFilter = f }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("类型", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KindFilter.entries.forEach { f ->
                    FilterChip(f.label, kindFilter == f) { kindFilter = f }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("${visible.size} 条 · 点按查看详情与前后采样", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                itemsIndexed(visible) { i, e ->
                    if (i > 0) Spacer(Modifier.height(1.dp).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)))
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = e }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp))
                            .background(levelColor(e.level)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.kind.title(), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(e.detail, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1)
                        }
                        Text(
                            SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(e.time)),
                            fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (visible.isEmpty()) {
                Text("当前筛选条件下没有事件", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
            }
        }
    }

    selected?.let { e ->
        EventDetailDialog(
            event = e,
            events = events,
            linkHistory = linkHistory,
            onDismiss = { selected = null }
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 10.sp,
            color = if (selected) Blue else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun EventDetailDialog(
    event: NetEvent,
    events: List<NetEvent>,
    linkHistory: List<LinkQualitySample>,
    onDismiss: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA) }
    val duration = remember(event, events) {
        if (event.kind == NetEventKind.OFFLINE) {
            val idx = events.indexOfFirst { it.time == event.time }
            val restore = if (idx > 0) events.subList(0, idx)
                .firstOrNull { it.kind == NetEventKind.ONLINE && it.time > event.time } else null
            restore?.let { it.time - event.time }
        } else null
    }
    val before = linkHistory.lastOrNull { it.time <= event.time }
    val after = linkHistory.firstOrNull { it.time >= event.time }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface).padding(18.dp)
        ) {
            Text("事件详情", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            DetailLine("类型", event.kind.title())
            DetailLine("级别", event.level.label)
            DetailLine("时间", fmt.format(Date(event.time)))
            duration?.let { DetailLine("持续时间", formatDuration(it)) }
            Spacer(Modifier.height(6.dp))
            Text("描述", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(event.detail, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.height(10.dp))
            Text("事件前后网络采样（来自链路历史，未测量则留空）",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SampleLine("之前", before)
            SampleLine("之后", after)
            Spacer(Modifier.height(12.dp))
            Text("关闭", fontSize = 11.sp, color = Blue,
                modifier = Modifier.align(Alignment.End).clickable { onDismiss() }.padding(4.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SampleLine(label: String, s: LinkQualitySample?) {
    val text = if (s == null) "无采样" else buildString {
        s.linkMbps?.let { append("$it Mbps  ") }
        s.rssi?.let { append("$it dBm  ") }
        s.latencyMs?.let { append("延迟 ${it.toInt()} ms  ") }
        s.jitterMs?.let { append("抖动 ${it.toInt()} ms  ") }
        s.lossPct?.let { append("丢包 ${it.toInt()}%") }
        if (isEmpty()) append("无可用指标")
    }
    DetailLine(label, text)
}

private fun isSameDay(time: Long, dayOffset: Int): Boolean {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, dayOffset) }
    val other = Calendar.getInstance().apply { this.timeInMillis = time }
    return cal.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return when {
        sec < 60 -> "$sec 秒"
        sec < 3600 -> "${sec / 60} 分 ${sec % 60} 秒"
        else -> "${sec / 3600} 小时 ${(sec % 3600) / 60} 分"
    }
}
