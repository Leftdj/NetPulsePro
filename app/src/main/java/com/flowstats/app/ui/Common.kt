package com.flowstats.app.ui

import androidx.compose.runtime.compositionLocalOf
import dev.chrisbanes.haze.HazeState
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.ui.theme.HeroColors
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
        b >= 1024L * 1024 -> String.format(Locale.US, "%.2f MB", b / (1024.0 * 1024.0))
        b >= 1024L -> String.format(Locale.US, "%.2f KB", b / 1024.0)
        else -> "$b B"
    }
}

fun formatSpeed(bytesPerSecond: Float): String =
    String.format(Locale.US, "%.2f Mbps", bytesPerSecond * 8f / 1_000_000f)

fun formatTime(millis: Long): String = when {
    millis <= 0L -> "未知"
    else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}

private val iconColors = listOf(
    Color(0xFF2BB19B), Color(0xFF2F80ED), Color(0xFF7B61FF), Color(0xFFED6C63),
    Color(0xFF12B7F5), Color(0xFFFF9800), Color(0xFF43A047), Color(0xFF5C6BC0)
)

@Composable
fun AppIcon(drawable: Drawable?, label: String, size: Dp = 42.dp, unknown: Boolean = false) {
    val background = remember(label, unknown) {
        if (unknown) Color(0xFF98A2B3)
        else {
            val hash = abs(label.hashCode() and 0x7fffffff)
            iconColors[hash % iconColors.size]
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape((size.value / 3.8).dp))
            .background(if (drawable == null) background else Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (drawable != null) {
            val bitmap = remember(drawable) { drawable.toBitmapCompat() }
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize())
        } else {
            Text(
                if (unknown) "?" else label.take(1).uppercase(Locale.getDefault()),
                color = Color.White,
                fontSize = (size.value / 2.3).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun Drawable.toBitmapCompat(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    bounds = Rect(0, 0, w, h)
    draw(canvas)
    return output
}

@Composable
fun AnimatedSplitBar(
    mobileRatio: Float,
    wifiRatio: Float,
    modifier: Modifier,
    mobileColor: Color = Orange,
    wifiColor: Color = Teal,
    trackColor: Color = Color(0xFF8A94A6).copy(alpha = 0.18f)
) {
    val m by animateFloatAsState(mobileRatio.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "mobile")
    val w by animateFloatAsState(wifiRatio.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "wifi")
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(trackColor, size = size, cornerRadius = CornerRadius(h / 2, h / 2))
        val mw = size.width * m
        val ww = size.width * w
        if (mw > 1f) drawRoundRect(mobileColor, size = Size(mw, h), cornerRadius = CornerRadius(h / 2, h / 2))
        if (ww > 1f) drawRoundRect(wifiColor, topLeft = Offset(mw, 0f), size = Size(ww, h), cornerRadius = CornerRadius(h / 2, h / 2))
    }
}

@Composable
fun AnimatedNumber(
    value: Float,
    modifier: Modifier = Modifier,
    decimals: Int = 2,
    suffix: String = "",
    style: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    color: Color = Color.Unspecified
) {
    val v by animateFloatAsState(value, tween(700, easing = FastOutSlowInEasing), label = "number")
    Text(
        String.format(Locale.US, "%.${decimals}f$suffix", v),
        modifier = modifier,
        style = style,
        color = color
    )
}

@Composable
fun RingChart(
    progress: Float,
    color: Color,
    modifier: Modifier,
    strokeWidth: Float = 9f,
    trackColor: Color = Color(0xFF8A94A6).copy(alpha = 0.18f),
    startAngle: Float = -90f
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "ring")
    Canvas(modifier) {
        if (trackColor != Color.Transparent) drawArc(trackColor, startAngle, 360f, false, style = Stroke(strokeWidth))
        if (p > 0f) drawArc(color, startAngle, 360f * p, false, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(Brush.linearGradient(HeroColors))) { content() }
}

@Composable
fun PulseDot(color: Color = Success, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition()
    val alpha by infinite.animateFloat(
        1f, 0.25f,
        infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse"
    )
    Canvas(modifier.size(8.dp)) {
        drawCircle(color.copy(alpha = alpha), radius = size.minDimension / 2)
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color,
    corner: Dp = 18.dp,
    elevation: Dp = 4.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .shadow(elevation, RoundedCornerShape(corner), clip = false)
            .clip(RoundedCornerShape(corner))
            .background(color)
    ) { content() }
}

/** 供顶栏等子组件访问外层 HazeState，实现跨页面的真实背景模糊 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * 液态玻璃质感容器（局部点缀用，不覆盖数据卡片）。
 *
 * 用「半透明底 + 渐变高光描边 + 顶部高光条」模拟玻璃折射观感：
 * - 不依赖 AGSL/RenderEffect，所有 Android 版本表现一致，无低版本降级突变；
 * - 无背景模糊，长列表滚动零额外开销；
 * - 适合顶栏、底栏、弹窗这类装饰性表面，数据区仍用 SoftCard 实色保证可读性。
 *
 * @param tint 底色；深浅主题由调用方传入对应 surface 色
 * @param alpha 半透明度，越小越"透"，建议 0.72~0.86
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tint: Color,
    corner: Dp = 18.dp,
    alpha: Float = 0.82f,
    glow: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .drawBehind {
                // 渐变高光描边：左上亮、右下淡，制造玻璃边缘的折射感
                if (glow) {
                    val stroke = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.28f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                    val w = 1.2.dp.toPx()
                    drawRoundRect(
                        brush = stroke,
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = Stroke(width = w),
                        size = Size(size.width - w, size.height - w),
                        topLeft = Offset(w / 2, w / 2)
                    )
                    // 顶部一条柔和高光，模拟玻璃上表面的反光
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0f)
                            ),
                            startY = 0f,
                            endY = size.height * 0.42f
                        ),
                        cornerRadius = CornerRadius(corner.toPx()),
                        size = Size(size.width, size.height * 0.42f)
                    )
                }
            }
            .clip(shape)
            .background(tint.copy(alpha = alpha))
    ) { content() }
}
