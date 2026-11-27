package com.flowstats.app.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlin.math.sin

/**
 * 液态玻璃调色板。
 *
 * 全部参数按主题派生，不写死颜色：浅色 / 深色 / AMOLED（纯黑背景）三套预设。
 * 目的：数据卡片保持实色可读，只有玻璃组件使用这套参数。
 */
data class LiquidPalette(
    val isDark: Boolean,
    val isAmoled: Boolean,
    /** Haze 背景模糊后叠加的底色（越不透明越像磨砂，越透明越能看见背后内容） */
    val hazeTint: Color,
    val blurRadius: Dp,
    /** 玻璃本体竖向渐变：顶部受光、底部沉暗 */
    val bodyTop: Color,
    val bodyMid: Color,
    val bodyBottom: Color,
    /** 边缘高光 / 暗边，构成玻璃厚度感 */
    val rimLight: Color,
    val rimMid: Color,
    val rimDark: Color,
    /** 细节点缀色 */
    val accent: Color,
    val accent2: Color,
    val specular: Color,
    val inactiveIcon: Color
)

@Composable
fun rememberLiquidPalette(): LiquidPalette {
    val scheme = MaterialTheme.colorScheme
    val bgLum = scheme.background.luminance()
    val isDark = bgLum < 0.5f
    val isAmoled = isDark && bgLum < 0.02f
    return remember(isDark, isAmoled, scheme.primary, scheme.surface) {
        if (isAmoled) {
            LiquidPalette(
                isDark = true, isAmoled = true,
                hazeTint = Color.Black.copy(alpha = 0.46f),
                blurRadius = 18.dp,
                bodyTop = Color.White.copy(alpha = 0.10f),
                bodyMid = Color.White.copy(alpha = 0.05f),
                bodyBottom = Color.White.copy(alpha = 0.02f),
                rimLight = Color.White.copy(alpha = 0.32f),
                rimMid = Color.White.copy(alpha = 0.06f),
                rimDark = Color.Black.copy(alpha = 0.55f),
                accent = scheme.primary,
                accent2 = Color(0xFF9B7BFF),
                specular = Color.White.copy(alpha = 0.55f),
                inactiveIcon = Color(0xFF9AA8C2)
            )
        } else if (isDark) {
            LiquidPalette(
                isDark = true, isAmoled = false,
                hazeTint = scheme.surface.copy(alpha = 0.40f),
                blurRadius = 22.dp,
                bodyTop = Color.White.copy(alpha = 0.13f),
                bodyMid = Color.White.copy(alpha = 0.07f),
                bodyBottom = Color.White.copy(alpha = 0.03f),
                rimLight = Color.White.copy(alpha = 0.38f),
                rimMid = Color.White.copy(alpha = 0.10f),
                rimDark = Color.Black.copy(alpha = 0.40f),
                accent = Color(0xFF6F97FF),
                accent2 = Color(0xFF9B7BFF),
                specular = Color.White.copy(alpha = 0.60f),
                inactiveIcon = Color(0xFF94A3BD)
            )
        } else {
            LiquidPalette(
                isDark = false, isAmoled = false,
                hazeTint = scheme.surface.copy(alpha = 0.30f),
                blurRadius = 26.dp,
                bodyTop = Color.White.copy(alpha = 0.26f),
                bodyMid = Color.White.copy(alpha = 0.14f),
                bodyBottom = Color.White.copy(alpha = 0.07f),
                rimLight = Color.White.copy(alpha = 0.95f),
                rimMid = Color.White.copy(alpha = 0.35f),
                rimDark = Color(0xFF3769F5).copy(alpha = 0.10f),
                accent = scheme.primary,
                accent2 = Color(0xFF7B4DFF),
                specular = Color.White.copy(alpha = 0.85f),
                inactiveIcon = Color(0xFF61708B)
            )
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
fun LiquidPalette.hazeStyle(): HazeStyle = HazeStyle(
    backgroundColor = Color.Transparent,
    tint = HazeTint(hazeTint),
    blurRadius = blurRadius,
    noiseFactor = if (isDark) 0.055f else 0.045f
)

/**
 * 液态胶囊路径。
 *
 * 两段半圆加上下两条带「腰」的贝塞尔桥：当左右两端拉远时腰身变细，
 * 形成液体被拉伸、尚未断开的状态；重合时退化为标准胶囊。
 * 这样切换时是一个形状在流动，而不是两个方块互相穿越。
 */
fun liquidPillPath(left: Float, right: Float, top: Float, bottom: Float): Path {
    val path = Path()
    val h = bottom - top
    val w = right - left
    if (h <= 0.5f || w <= 0.5f) return path
    val rad = h / 2f
    if (w <= h + 0.5f) {
        path.addRoundRect(
            RoundRect(left, top, right, bottom, CornerRadius(rad, rad))
        )
        return path
    }
    val lc = left + rad
    val rc = right - rad
    val d = rc - lc
    // 圆心越远，腰部收得越细（0 = 完全融合，1 = 拉成细颈）
    val grip = (d / (rad * 3.2f)).coerceIn(0f, 1f)
    val waist = rad * 0.52f * grip
    val k = d * 0.34f
    path.moveTo(lc, top)
    path.cubicTo(lc + k, top + waist, rc - k, top + waist, rc, top)
    path.arcTo(Rect(rc - rad, top, rc + rad, bottom), -90f, 180f, false)
    path.cubicTo(rc - k, bottom - waist, lc + k, bottom - waist, lc, bottom)
    path.arcTo(Rect(lc - rad, top, lc + rad, bottom), 90f, 180f, false)
    path.close()
    return path
}

/** 玻璃本体：竖向受光渐变 + 顶部内高光 + 底部内侧折射反光 */
fun DrawScope.drawGlassBody(palette: LiquidPalette, corner: Dp) {
    val r = cornerPx(corner)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(palette.bodyTop, palette.bodyMid, palette.bodyBottom),
            startY = 0f, endY = size.height
        ),
        cornerRadius = CornerRadius(r, r)
    )
    // 顶部受光带：模拟光源在上方
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(palette.specular.copy(alpha = palette.specular.alpha * 0.30f), Color.Transparent),
            startY = 0f, endY = size.height * 0.55f
        ),
        cornerRadius = CornerRadius(r, r)
    )
    // 底部内侧折射：玻璃厚边把环境光带回来
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, palette.accent.copy(alpha = 0.055f)),
            startY = size.height * 0.62f, endY = size.height
        ),
        cornerRadius = CornerRadius(r, r)
    )
}

/** 玻璃边缘：斜向渐变描边，左上亮、右下暗，形成高光与厚度的对比 */
fun DrawScope.drawGlassRim(palette: LiquidPalette, corner: Dp) {
    val inset = 0.7.dp.toPx()
    val r = (cornerPx(corner) - inset).coerceAtLeast(0f)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                palette.rimLight,
                palette.rimMid,
                palette.rimDark,
                palette.rimMid,
                palette.rimLight.copy(alpha = palette.rimLight.alpha * 0.55f)
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        ),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.15.dp.toPx())
    )
}

/**
 * 选中胶囊：底层色玻璃 + 顶面高光 + 底部折射 + 沿表面掠过的流光 + 玻璃描边。
 *
 * [squash] 0..1 按压深度：横向拉开 6%、纵向压扁 16%，中心不动——像软胶囊被指腹压住；
 * [sheen]  0..1 流光行程，用 sin 做淡入淡出，掠过即归于平静。
 */
fun DrawScope.drawLiquidPill(
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    squash: Float,
    palette: LiquidPalette,
    sheen: Float
) {
    if (right - left < 1f || bottom - top < 1f) return
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val halfW = (right - left) / 2f * (1f + 0.06f * squash)
    val halfH = (bottom - top) / 2f * (1f - 0.16f * squash)
    val l = cx - halfW
    val r = cx + halfW
    val t = cy - halfH
    val b = cy + halfH
    val path = liquidPillPath(l, r, t, b)
    val h = b - t

    // 1. 色玻璃底
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            listOf(
                palette.accent.copy(alpha = 0.30f),
                palette.accent.copy(alpha = 0.20f),
                palette.accent2.copy(alpha = 0.13f)
            ),
            startY = t, endY = b
        )
    )
    // 2. 顶面高光
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            listOf(palette.specular.copy(alpha = 0.34f), Color.Transparent),
            startY = t, endY = t + h * 0.62f
        )
    )
    // 3. 表面细节：折射色边 + 流光（裁剪在胶囊内部）
    clipPath(path) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, palette.accent.copy(alpha = 0.26f)),
                startY = b - h * 0.38f, endY = b
            ),
            topLeft = Offset(l, b - h * 0.38f),
            size = Size(r - l, h * 0.38f)
        )
        if (sheen in 0f..1f) {
            val band = (r - l) * 0.62f
            val sx = l - band + (r - l + band * 2f) * sheen
            val fade = sin(Math.PI * sheen).toFloat().coerceIn(0f, 1f)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.30f * fade), Color.Transparent),
                    startX = sx, endX = sx + band
                ),
                topLeft = Offset(l, t),
                size = Size(r - l, h)
            )
        }
    }
    // 4. 玻璃描边
    drawPath(path, palette.specular.copy(alpha = 0.50f), style = Stroke(width = 1.1.dp.toPx()))
}

private fun DrawScope.cornerPx(corner: Dp): Float =
    corner.toPx().coerceAtMost(size.height / 2f)

/**
 * 可复用的液态玻璃表面：本体在下、描边在上，内容绘制在两者之间。
 * 后续阶段（悬浮窗、弹窗、按钮）直接套这个 Modifier 即可保持材质一致。
 */
fun Modifier.liquidGlass(palette: LiquidPalette, corner: Dp): Modifier = this
    .drawBehind { drawGlassBody(palette, corner) }
    .drawWithContent {
        drawContent()
        drawGlassRim(palette, corner)
    }

/**
 * 是否开启「移除动画」（开发者选项 / 无障碍里的动画缩放设为 0）。
 * 开启时只做位形切换，不播放流光与回弹，避免给前庭敏感用户造成不适。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun readScale(): Boolean = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)

        reduce = readScale()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = readScale()
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false, observer
            )
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
    return reduce
}
