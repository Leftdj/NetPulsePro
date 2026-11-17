package com.flowstats.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import com.flowstats.app.ui.theme.Success

/** HyperOS 风格的区块标题：左侧短竖条 + 标题，右侧可选副操作文案 */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 14.5.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                trailing,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 指标块：图标 pill + 数值 + 标签。
 * 数值为空时显示「不支持」，不填占位数字。
 */
@Composable
fun MetricTile(
    glyph: String,
    label: String,
    value: String?,
    unit: String? = null,
    accent: Color,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    SoftCard(
        modifier,
        color = MaterialTheme.colorScheme.surface,
        corner = 20.dp,
        elevation = 2.dp
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, fontSize = 13.sp, color = accent)
            }
            Spacer(Modifier.height(9.dp))
            if (value == null) {
                Text(
                    "设备不支持",
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (unit != null) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            unit,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                hint ?: label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 键值行：值为 null 时统一显示「设备不支持」，这是本 App 对不可得字段的唯一表达方式 */
@Composable
fun InfoRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    fallback: String = "设备不支持"
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 36.dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        if (value == null) {
            Text(
                fallback,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        } else {
            Text(
                value,
                fontSize = if (mono) 11.5.sp else 12.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(2.2f, fill = false)
            )
        }
    }
}

@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier.fillMaxWidth().padding(start = inset).height(0.7.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    )
}

/** 四格信号条，level 0..4，null 表示读不到 */
@Composable
fun SignalBars(level: Int?, modifier: Modifier = Modifier, color: Color = Success) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..3) {
            val active = level != null && i <= level
            Box(
                Modifier.width(3.dp).height((5 + i * 3).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (active) color else color.copy(alpha = 0.16f))
            )
        }
    }
}

/** 迷你柱状图，用于 ping 样本分布；数据为空时不绘制假波形 */
@Composable
fun SparkBars(samples: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (samples.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无样本", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        return
    }
    val peak = (samples.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val count = samples.size
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        samples.forEachIndexed { i, value ->
            val ratio = (value / peak).toFloat().coerceIn(0.06f, 1f)
            val h = size.height * ratio
            drawRoundRect(
                color = if (i == samples.lastIndex) color else color.copy(alpha = 0.35f),
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(barWidth, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
