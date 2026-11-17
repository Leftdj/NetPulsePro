package com.flowstats.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.Totals
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Teal
import java.util.Calendar
import java.util.Locale

/**
 * 底部月历选择器。
 *
 * 交互约定（重做后）：
 * - 点某一天立即生效并关闭，不再需要二次点「查看该日流量」；
 * - 翻月不会丢失选中态，但只有落在当前可见月份的那天才高亮；
 * - 「今天」一键回到今天；未来月份与未来日期都点不动；
 * - 每天下方的小条表示当天用量：橙色偏移动、青色偏 WiFi。
 *
 * @param anchorMillis 当前查看区间起点，用来定位高亮与初始月份
 * @param heat dayOfMonth -> 当天流量，由 ViewModel 按月缓存后传入
 * @param onVisibleMonth 翻到某个月时回调，用于按需拉取该月热力
 */
@Composable
fun DatePickerSheet(
    visible: Boolean,
    anchorMillis: Long,
    heat: Map<Int, Totals>,
    onDismiss: () -> Unit,
    onPick: (year: Int, month: Int, day: Int) -> Unit,
    onVisibleMonth: (year: Int, month: Int) -> Unit = { _, _ -> }
) {
    val today = remember(visible) { Calendar.getInstance(Locale.CHINA) }
    val anchor = remember(anchorMillis, visible) {
        Calendar.getInstance(Locale.CHINA).apply { timeInMillis = anchorMillis }
    }
    // 关键：把 anchorMillis 也作为 key，切换周期后再打开会重新定位到当前区间
    var year by remember(visible, anchorMillis) { mutableStateOf(anchor.get(Calendar.YEAR)) }
    var month by remember(visible, anchorMillis) { mutableStateOf(anchor.get(Calendar.MONTH)) }

    val selY = anchor.get(Calendar.YEAR)
    val selM = anchor.get(Calendar.MONTH)
    val selD = anchor.get(Calendar.DAY_OF_MONTH)

    val currentYear = today.get(Calendar.YEAR)
    val currentMonth = today.get(Calendar.MONTH)
    val atCurrentMonth = year * 12 + month >= currentYear * 12 + currentMonth

    LaunchedEffect(visible, year, month) {
        if (visible) onVisibleMonth(year, month)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                    indication = null, onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(300)) { it },
                exit = slideOutVertically(tween(250)) { it }
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                            indication = null, onClick = {}
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "选择日期", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(Blue.copy(alpha = 0.10f))
                                .clickable {
                                    onPick(currentYear, currentMonth, today.get(Calendar.DAY_OF_MONTH))
                                }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Text("今天", fontSize = 11.sp, color = Blue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // 翻月
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NavArrow("‹", enabled = true) {
                            val c = Calendar.getInstance(Locale.CHINA).apply { set(year, month, 1) }
                            c.add(Calendar.MONTH, -1)
                            year = c.get(Calendar.YEAR)
                            month = c.get(Calendar.MONTH)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$year 年 ${month + 1} 月", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(108.dp), textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(10.dp))
                        NavArrow("›", enabled = !atCurrentMonth) {
                            val c = Calendar.getInstance(Locale.CHINA).apply { set(year, month, 1) }
                            c.add(Calendar.MONTH, 1)
                            if (c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) <=
                                currentYear * 12 + currentMonth
                            ) {
                                year = c.get(Calendar.YEAR)
                                month = c.get(Calendar.MONTH)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                            Text(
                                it, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    CalendarGrid(
                        year = year, month = month,
                        today = today,
                        selY = selY, selM = selM, selD = selD,
                        heat = heat,
                        onPick = onPick
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "点按日期立即查看 · 橙色偏移动、青色偏 WiFi",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun NavArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f)
        )
    }
}

@Composable
private fun CalendarGrid(
    year: Int, month: Int, today: Calendar,
    selY: Int, selM: Int, selD: Int,
    heat: Map<Int, Totals>,
    onPick: (Int, Int, Int) -> Unit
) {
    val first = Calendar.getInstance(Locale.CHINA).apply {
        set(year, month, 1, 0, 0, 0); clear(Calendar.MILLISECOND)
        firstDayOfWeek = Calendar.MONDAY
    }
    val dow = (first.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val todayStart = Calendar.getInstance(Locale.CHINA).apply {
        set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        clear(Calendar.MILLISECOND)
    }
    val rows = (dow + daysInMonth + 6) / 7
    val maxHeat = heat.values.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L

    Column {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().height(42.dp)) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val dayNum = index - dow + 1
                    if (dayNum in 1..daysInMonth) {
                        val cellCal = Calendar.getInstance(Locale.CHINA).apply {
                            set(year, month, dayNum, 0, 0, 0); clear(Calendar.MILLISECOND)
                        }
                        val isFuture = cellCal.timeInMillis > todayStart.timeInMillis
                        val isSelected = year == selY && month == selM && dayNum == selD
                        val isToday = year == today.get(Calendar.YEAR) &&
                            month == today.get(Calendar.MONTH) && dayNum == today.get(Calendar.DAY_OF_MONTH)
                        val dayTotal = heat[dayNum]
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> Brush.horizontalGradient(listOf(Blue, Purple))
                                            isToday -> Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                )
                                            )
                                            else -> Brush.horizontalGradient(
                                                listOf(Color.Transparent, Color.Transparent)
                                            )
                                        }
                                    )
                                    .clickable(enabled = !isFuture) { onPick(year, month, dayNum) }
                                    .padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "$dayNum",
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                                        isSelected -> Color.White
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(Modifier.height(2.dp))
                                // 当天用量指示条：无流量留空，保证网格不被撑歪
                                val barColor = when {
                                    isSelected -> Color.White.copy(alpha = 0.85f)
                                    dayTotal == null || dayTotal.total <= 0 -> Color.Transparent
                                    dayTotal.mobile > dayTotal.wifi * 2 -> Orange
                                    dayTotal.wifi > dayTotal.mobile * 2 -> Teal
                                    else -> Purple
                                }
                                val alpha = if (dayTotal == null || dayTotal.total <= 0) 0f
                                else (0.35f + 0.65f * (dayTotal.total.toFloat() / maxHeat)).coerceIn(0.35f, 1f)
                                Box(
                                    Modifier.width(14.dp).height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(barColor.copy(alpha = if (isSelected) 0.85f else alpha))
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
