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
import com.flowstats.app.data.Period
import com.flowstats.app.data.Totals
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Teal
import java.util.Calendar
import java.util.Locale

/** 年份下限：再往前没有任何系统流量记录可查，避免无限翻页 */
private const val MIN_YEAR = 2000

/**
 * 底部日期选择器，按当前周期自适应：
 *
 * - 日 / 周：月历网格，点某天立即生效（下方小条表示当天用量，橙色偏移动、青色偏 WiFi）；
 * - 月：年 + 12 个月网格，直接点某月跳到该月，不用一个月一个月翻；
 * - 年：12 年网格，直接点某年。
 *
 * 统一约定：点选立即生效并关闭；翻页不会丢失选中态；未来日期/月份/年份点不动。
 * 月/年模式只负责「选到哪个区间」，具体偏移仍由 ViewModel 用仓库的区间换算得出，口径与左右箭头一致。
 *
 * @param anchorMillis 当前查看区间起点，用来定位高亮与初始年月
 * @param heat dayOfMonth -> 当天流量，由 ViewModel 按月缓存后传入（仅日/周模式使用）
 * @param onVisibleMonth 翻到某个月时回调，用于按需拉取该月热力（仅日/周模式使用）
 */
@Composable
fun DatePickerSheet(
    visible: Boolean,
    anchorMillis: Long,
    heat: Map<Int, Totals>,
    onDismiss: () -> Unit,
    onPick: (year: Int, month: Int, day: Int) -> Unit,
    onVisibleMonth: (year: Int, month: Int) -> Unit = { _, _ -> },
    period: Period = Period.DAY
) {
    val today = remember(visible) { Calendar.getInstance(Locale.CHINA) }
    val anchor = remember(anchorMillis, visible) {
        Calendar.getInstance(Locale.CHINA).apply { timeInMillis = anchorMillis }
    }
    // 关键：把 anchorMillis 也作为 key，切换周期后再打开会重新定位到当前区间
    var year by remember(visible, anchorMillis, period) { mutableStateOf(anchor.get(Calendar.YEAR)) }
    var month by remember(visible, anchorMillis, period) { mutableStateOf(anchor.get(Calendar.MONTH)) }
    // 年模式的翻页起点：默认让当前年落在第二行，前后都能一眼看到
    var pageStart by remember(visible, anchorMillis, period) {
        mutableStateOf(anchor.get(Calendar.YEAR) - 4)
    }

    val selY = anchor.get(Calendar.YEAR)
    val selM = anchor.get(Calendar.MONTH)
    val selD = anchor.get(Calendar.DAY_OF_MONTH)

    val currentYear = today.get(Calendar.YEAR)
    val currentMonth = today.get(Calendar.MONTH)
    val atCurrentMonth = year * 12 + month >= currentYear * 12 + currentMonth

    LaunchedEffect(visible, year, month, period) {
        if (visible && (period == Period.DAY || period == Period.WEEK)) onVisibleMonth(year, month)
    }

    val title = when (period) {
        Period.DAY, Period.WEEK -> "选择日期"
        Period.MONTH -> "选择月份"
        Period.YEAR -> "选择年份"
    }
    val quickLabel = when (period) {
        Period.DAY, Period.WEEK -> "今天"
        Period.MONTH -> "本月"
        Period.YEAR -> "今年"
    }
    val quickPick: () -> Unit = when (period) {
        Period.DAY, Period.WEEK -> { { onPick(currentYear, currentMonth, today.get(Calendar.DAY_OF_MONTH)) } }
        Period.MONTH -> { { onPick(currentYear, currentMonth, 1) } }
        Period.YEAR -> { { onPick(currentYear, 0, 1) } }
    }
    val footnote = when (period) {
        Period.DAY, Period.WEEK -> "点按日期立即查看 · 橙色偏移动、青色偏 WiFi"
        Period.MONTH -> "点按月份立即查看 · 与左右箭头逐月切换结果一致"
        Period.YEAR -> "点按年份立即查看 · 只能选到今年"
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
                            title, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(Blue.copy(alpha = 0.10f))
                                .clickable { quickPick() }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Text(quickLabel, fontSize = 11.sp, color = Blue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    when (period) {
                        Period.DAY, Period.WEEK -> DaySection(
                            year = year, month = month,
                            today = today,
                            selY = selY, selM = selM, selD = selD,
                            atCurrentMonth = atCurrentMonth,
                            heat = heat,
                            onPrevMonth = {
                                val c = Calendar.getInstance(Locale.CHINA).apply { set(year, month, 1) }
                                c.add(Calendar.MONTH, -1)
                                year = c.get(Calendar.YEAR)
                                month = c.get(Calendar.MONTH)
                            },
                            onNextMonth = {
                                val c = Calendar.getInstance(Locale.CHINA).apply { set(year, month, 1) }
                                c.add(Calendar.MONTH, 1)
                                if (c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) <=
                                    currentYear * 12 + currentMonth
                                ) {
                                    year = c.get(Calendar.YEAR)
                                    month = c.get(Calendar.MONTH)
                                }
                            },
                            onPick = onPick
                        )

                        Period.MONTH -> MonthSection(
                            year = year,
                            selY = selY, selM = selM,
                            currentYear = currentYear, currentMonth = currentMonth,
                            onPrevYear = { if (year > MIN_YEAR) year -= 1 },
                            onNextYear = { if (year < currentYear) year += 1 },
                            onPickMonth = { m -> onPick(year, m, 1) }
                        )

                        Period.YEAR -> YearSection(
                            pageStart = pageStart,
                            selY = selY, currentYear = currentYear,
                            onPrevPage = { if (pageStart - 12 >= MIN_YEAR) pageStart -= 12 },
                            onNextPage = { if (pageStart + 12 <= currentYear) pageStart += 12 },
                            onPickYear = { y -> onPick(y, 0, 1) }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        footnote,
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

/** 日 / 周模式：月历网格 */
@Composable
private fun DaySection(
    year: Int, month: Int,
    today: Calendar,
    selY: Int, selM: Int, selD: Int,
    atCurrentMonth: Boolean,
    heat: Map<Int, Totals>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPick: (Int, Int, Int) -> Unit
) {
    // 翻月
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NavArrow("‹", enabled = true) { onPrevMonth() }
        Spacer(Modifier.width(10.dp))
        Text(
            "$year 年 ${month + 1} 月", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(108.dp), textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(10.dp))
        NavArrow("›", enabled = !atCurrentMonth) { onNextMonth() }
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
}

/** 月模式：年 + 12 个月网格，直接命中目标月 */
@Composable
private fun MonthSection(
    year: Int,
    selY: Int, selM: Int,
    currentYear: Int, currentMonth: Int,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onPickMonth: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NavArrow("‹", enabled = year > MIN_YEAR) { onPrevYear() }
        Spacer(Modifier.width(10.dp))
        Text(
            "$year 年", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(108.dp), textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(10.dp))
        NavArrow("›", enabled = year < currentYear) { onNextYear() }
    }
    Spacer(Modifier.height(12.dp))

    // 4 列 × 3 行：3/6/9/12 月各占行末，扫读顺序和日历一致
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val m = row * 4 + col
                    val isFuture = year * 12 + m > currentYear * 12 + currentMonth
                    val isSelected = year == selY && m == selM
                    val isCurrent = year == currentYear && m == currentMonth
                    PickCell(
                        label = "${m + 1} 月",
                        selected = isSelected,
                        isCurrent = isCurrent,
                        enabled = !isFuture,
                        modifier = Modifier.weight(1f),
                        onClick = { onPickMonth(m) }
                    )
                }
            }
        }
    }
}

/** 年模式：12 年网格，按页翻动 */
@Composable
private fun YearSection(
    pageStart: Int,
    selY: Int, currentYear: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onPickYear: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        NavArrow("‹", enabled = pageStart - 12 >= MIN_YEAR) { onPrevPage() }
        Spacer(Modifier.width(10.dp))
        Text(
            "$pageStart – ${pageStart + 11}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(126.dp), textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(10.dp))
        NavArrow("›", enabled = pageStart + 12 <= currentYear) { onNextPage() }
    }
    Spacer(Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    val y = pageStart + row * 4 + col
                    PickCell(
                        label = "$y",
                        selected = y == selY,
                        isCurrent = y == currentYear,
                        enabled = y <= currentYear,
                        modifier = Modifier.weight(1f),
                        onClick = { onPickYear(y) }
                    )
                }
            }
        }
    }
}

/** 月/年网格里的单元格：选中为蓝紫渐变，今年/本月用淡底标记 */
@Composable
private fun PickCell(
    label: String,
    selected: Boolean,
    isCurrent: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> Brush.horizontalGradient(listOf(Blue, Purple))
                    isCurrent -> Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                    else -> Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                selected -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
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
