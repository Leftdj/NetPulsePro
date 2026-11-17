package com.flowstats.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.AppUsage
import com.flowstats.app.data.Period
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Teal

@Composable
fun AppsScreen(
    state: UiState,
    onMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onPeriod: (Period) -> Unit,
    onShiftDate: (Int) -> Unit,
    onPickDate: (Int, Int, Int) -> Unit,
    onCalendarMonth: (Int, Int) -> Unit,
    onOpenApp: (AppUsage) -> Unit,
    onQuery: (String) -> Unit,
    onSort: (SortBy) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onRefresh: () -> Unit
) {
    var sortOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = calendarOpen || sortOpen) {
        calendarOpen = false
        sortOpen = false
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("应用统计", onMenu = onMenu, onAction = onOpenSettings)
            if (!state.permissionGranted) PermissionBanner(onOpenSettings)

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    if (state.query.isEmpty()) {
                        Text("搜索应用名称 / 包名", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (state.query.isNotEmpty()) {
                    Text("×", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onQuery("") })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp)
            ) {
                Period.entries.forEach { p ->
                    val selected = p == state.period
                    val indicator by animateDpAsState(if (selected) 24.dp else 0.dp, tween(300), label = "tab")
                    Box(
                        Modifier.weight(1f).height(36.dp).clickable { onPeriod(p) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                Modifier.align(Alignment.Center)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.horizontalGradient(listOf(Blue, Purple)))
                                    .fillMaxWidth()
                                    .height(36.dp)
                            )
                        }
                        Text(
                            p.label, fontSize = 13.sp,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Box(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp)
                                .width(indicator).height(0.dp)
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryBox(formatBytes(state.summary.mobile), "移动网络", listOf(Color(0xFFFFB457), Orange), Modifier.weight(1f))
                SummaryBox(formatBytes(state.summary.wifi), "WiFi 网络", listOf(Color(0xFF2AD4C4), Teal), Modifier.weight(1f))
                SummaryBox(formatBytes(state.summary.total), "总流量", listOf(Color(0xFF6D7FF5), Color(0xFF4858C9)), Modifier.weight(1f))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppFilter.entries.forEach { f ->
                    val on = f == state.filter
                    Box(
                        Modifier.clip(RoundedCornerShape(11.dp))
                            .background(if (on) Blue else MaterialTheme.colorScheme.surface)
                            .clickable { onFilter(f) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(f.label, fontSize = 11.sp, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { sortOpen = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) { Text("⇅ 排序", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(8.dp))
                val rotation by animateFloatAsState(if (state.refreshing) 360f else 0f, tween(700), label = "refresh")
                Box(
                    Modifier.clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onRefresh() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("↻", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer(rotationZ = rotation))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onShiftDate(-1) }.padding(horizontal = 8.dp))
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { calendarOpen = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("▦", fontSize = 13.sp, color = Blue)
                    Spacer(Modifier.width(6.dp))
                    Text(state.dateLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onShiftDate(1) }.padding(horizontal = 8.dp))
            }

            Crossfade(targetState = Triple(state.period, state.filter, state.query), label = "list") {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (state.message != null) {
                        item {
                            Text(state.message!!, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp, modifier = Modifier.padding(18.dp))
                        }
                    }
                    itemsIndexed(state.visibleApps, key = { _, app -> app.uid }) { index, app ->
                        AppRow(app, index, state.visibleApps.maxOfOrNull { it.total }?.toFloat()?.coerceAtLeast(1f) ?: 1f) { onOpenApp(app) }
                    }
                    item {
                        Text("橙色 = 移动网络　青色 = WiFi", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(20.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = sortOpen,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable { sortOpen = false })
        }
        AnimatedVisibility(
            visible = sortOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(18.dp)
            ) {
                Text("排序方式", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                SortBy.entries.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSort(s); sortOpen = false }
                            .padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (s == state.sortBy) {
                            Text("✓", color = Blue, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        DatePickerSheet(
            visible = calendarOpen,
            anchorMillis = state.viewDate,
            heat = state.monthHeat,
            onDismiss = { calendarOpen = false },
            onPick = { y, m, d -> onPickDate(y, m, d); calendarOpen = false },
            onVisibleMonth = onCalendarMonth
        )
    }
}

@Composable
private fun SummaryBox(value: String, label: String, colors: List<Color>, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(colors))
    ) {
        Column(Modifier.padding(vertical = 13.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, color = Color.White.copy(alpha = 0.92f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun AppRow(app: AppUsage, index: Int, max: Float, onClick: () -> Unit) {
    SoftCard(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.icon, app.label, 42.dp, unknown = app.isUnknown)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (index < 3) "${index + 1}. ${app.label}" else app.label,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (index < 3) Blue else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatBytes(app.total), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                if (app.isUnknown) {
                    Text("UID ${app.uid} · 系统残留或已卸载", fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 3.dp))
                }
                Spacer(Modifier.height(if (app.isUnknown) 7.dp else 9.dp))
                AnimatedSplitBar(
                    mobileRatio = app.mobile.toFloat() / max,
                    wifiRatio = app.wifi.toFloat() / max,
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Spacer(Modifier.height(7.dp))
                val share = app.total.coerceAtLeast(1L)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("移动 ${formatBytes(app.mobile)} · ${app.mobile * 100 / share}%",
                        fontSize = 10.sp, color = Orange)
                    Text("WiFi ${formatBytes(app.wifi)} · ${app.wifi * 100 / share}%",
                        fontSize = 10.sp, color = Teal)
                }
            }
        }
    }
}
