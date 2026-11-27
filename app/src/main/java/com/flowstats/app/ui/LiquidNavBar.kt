package com.flowstats.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 液态玻璃底部导航栏（按参考视频重做）。
 *
 * 视觉：
 * - 通透玻璃：Haze 真实背景模糊 + 低不透明度底色，能看见背后的页面内容；
 * - 选中态：淡蓝水滴形圆角块停在当前标签背后，图标与文字变蓝；
 *   切换时块以弹簧滑到新位置，并「先胀后收」——液体被带走的感觉；
 * - 点击涟漪：手指按下位置扩散白色圆盘 + 两道圆环波纹，随即归于平静。
 *
 * 交互：
 * - 单击选中（松手时判定，按下不动选中项，避免滑动页面时误切）；
 * - 长按 0.5s 后左右拖动连续切换，带触感反馈，两端有橡皮筋；
 * - 按下整条玻璃轻微压扁下沉，松手弹簧回弹；
 * - 关闭动画（系统动画缩放 = 0）时只做位形切换。
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun LiquidNavBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    height: Dp = 68.dp,
    icon: @Composable (index: Int, tint: Color, modifier: Modifier) -> Unit
) {
    val palette = rememberLiquidPalette()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    val capsule = RoundedCornerShape(percent = 50)

    // 选中高亮块：中心 x（px），弹簧补间
    val hlX = remember { Animatable(0f) }
    // 液态胀缩：切换时先胀开再收拢
    val stretch = remember { Animatable(0f) }
    // 按压深度：0 常态，1 压到最深
    val squash = remember { Animatable(0f) }
    // 涟漪进度：>=1 表示当前没有涟漪
    val ripple = remember { Animatable(1f) }
    var rippleCx by remember { mutableFloatStateOf(0f) }

    var dragging by remember { mutableStateOf(false) }
    var dragCx by remember { mutableFloatStateOf(0f) }
    val cellW = remember { mutableFloatStateOf(0f) }
    val selNow = rememberUpdatedState(selectedIndex)

    fun fireRipple(xPx: Float) {
        if (reduceMotion) return
        rippleCx = xPx
        scope.launch {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(durationMillis = 560, easing = LinearOutSlowInEasing))
        }
    }

    // 选中项变化 → 高亮块弹簧滑到新格中心
    LaunchedEffect(selectedIndex, cellW.floatValue) {
        if (cellW.floatValue > 0f) {
            hlX.animateTo(
                selectedIndex * cellW.floatValue + cellW.floatValue / 2f,
                if (reduceMotion) tween(durationMillis = 0)
                else spring(dampingRatio = 0.55f, stiffness = 320f)
            )
        }
    }
    // 切换瞬间：先胀后收（液体被带走）
    LaunchedEffect(selectedIndex) {
        if (reduceMotion) {
            stretch.snapTo(0f)
            return@LaunchedEffect
        }
        stretch.snapTo(0f)
        stretch.animateTo(1f, tween(durationMillis = 150, easing = FastOutLinearInEasing))
        stretch.animateTo(0f, tween(durationMillis = 340, easing = LinearOutSlowInEasing))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                // 整条玻璃随按压轻微下沉，幅度远小于胶囊本身，保持克制
                val press = squash.value
                scaleX = 1f + 0.012f * press
                scaleY = 1f - 0.020f * press
                translationY = 1.2.dp.toPx() * press
            }
            // 极淡的投影：只为了离开内容表面，不用重阴影
            .shadow(
                elevation = 10.dp,
                shape = capsule,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color(0xFF3769F5).copy(alpha = 0.10f)
            )
            .clip(capsule)
            .hazeEffect(state = hazeState, style = palette.hazeStyle())
            .liquidGlass(palette, corner = height / 2)
            .pointerInput(labels.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val startX = down.position.x
                    var moved = false
                    var longPressed = false
                    var lastX = startX
                    scope.launch {
                        squash.animateTo(
                            if (reduceMotion) 0f else 1f,
                            spring(dampingRatio = 0.55f, stiffness = 1400f)
                        )
                    }
                    // 按下瞬间：从指腹位置扩散涟漪
                    fireRipple(startX)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!longPressed) {
                            if (abs(change.position.x - startX) > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            if (change.uptimeMillis - down.uptimeMillis >=
                                viewConfiguration.longPressTimeoutMillis
                            ) {
                                longPressed = true
                                dragging = true
                                dragCx = hlX.value
                                lastX = change.position.x
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        } else {
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            if (dx != 0f) {
                                change.consume()
                                val w = cellW.floatValue
                                dragCx = (dragCx + dx).coerceIn(
                                    w / 2f, labels.lastIndex * w + w / 2f
                                )
                                val idx = (dragCx / w).toInt().coerceIn(0, labels.lastIndex)
                                if (idx != selNow.value) {
                                    onSelect(idx)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // 每切过一格，就在新的高亮位置再来一粒涟漪
                                    fireRipple(dragCx)
                                }
                            }
                        }
                    }
                    // 松手·回弹
                    scope.launch {
                        squash.animateTo(
                            0f,
                            if (reduceMotion) tween(durationMillis = 0)
                            else spring(dampingRatio = 0.42f, stiffness = 380f)
                        )
                    }
                    if (longPressed) {
                        dragging = false
                        scope.launch {
                            hlX.animateTo(
                                selNow.value * cellW.floatValue + cellW.floatValue / 2f,
                                spring(dampingRatio = 0.52f, stiffness = 300f)
                            )
                        }
                    } else if (!moved && cellW.floatValue > 0f) {
                        // 单击：按下不选中，松手才判定，避免拖动内容时误切页面
                        val idx = (startX / cellW.floatValue).toInt().coerceIn(0, labels.lastIndex)
                        if (idx != selNow.value) onSelect(idx)
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val itemW = widthPx / labels.size
        SideEffect { cellW.floatValue = itemW }
        val selected by rememberUpdatedState(selectedIndex)

        // 选中高亮块：淡蓝水滴形圆角块，画在图标文字之下
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / labels.size
            val cx = if (dragging) dragCx else hlX.value
            // 液态胀缩：切换瞬间横向胀开 30%，随后收拢
            val blockW = w * 0.68f * (1f + 0.30f * stretch.value)
            val halfH = (size.height / 2f - 10.dp.toPx()) * (1f - 0.12f * squash.value)
            val cy = size.height / 2f
            val l = cx - blockW / 2f
            val r = cx + blockW / 2f
            val t = cy - halfH
            val b = cy + halfH
            if (r - l < 1f || b - t < 1f) return@Canvas
            // 宽高接近时 liquidPillPath 退化为圆角矩形（视频里的水滴块形状）
            val path = liquidPillPath(l, r, t, b)
            // 1. 淡蓝色玻璃底
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    listOf(
                        palette.accent.copy(alpha = 0.26f),
                        palette.accent.copy(alpha = 0.16f)
                    ),
                    startY = t, endY = b
                )
            )
            // 2. 顶面受光高光
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                    startY = t, endY = t + (b - t) * 0.55f
                )
            )
            // 3. 玻璃描边：白色边缘高光
            drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.1.dp.toPx()))
        }

        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { i, label ->
                val on = i == selectedIndex
                val tint by animateColorAsState(
                    targetValue = if (on) palette.accent else palette.inactiveIcon,
                    animationSpec = tween(if (reduceMotion) 0 else 240),
                    label = "navTint$i"
                )
                val scale by animateFloatAsState(
                    targetValue = if (on) 1f else 0.94f,
                    animationSpec = if (reduceMotion) tween(durationMillis = 0)
                    else spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "navScale$i"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics {
                            this.selected = on
                            role = Role.Tab
                            contentDescription = label
                            onClick {
                                if (!on) onSelect(i)
                                true
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(22.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        icon(i, tint, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        color = tint,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // 点击涟漪：白色圆盘 + 两道圆环波纹，从指腹位置扩散后归于平静
        // 画在最上层（BoxWithConstraints 已 clip 到胶囊形状，不会溢出）
        Canvas(Modifier.fillMaxSize()) {
            val p = ripple.value
            if (p >= 1f) return@Canvas
            val maxR = size.width * 0.62f
            val center = Offset(rippleCx, size.height / 2f)
            val fade = 1f - p
            // 圆盘：中心浓、边缘淡
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f * fade),
                        Color.White.copy(alpha = 0.10f * fade),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR
                ),
                radius = maxR * p,
                center = center
            )
            // 第一道波纹
            drawCircle(
                color = Color.White.copy(alpha = 0.42f * fade),
                radius = maxR * p,
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )
            // 第二道更细、更慢的波纹
            drawCircle(
                color = Color.White.copy(alpha = 0.26f * fade),
                radius = maxR * p * 0.62f,
                center = center,
                style = Stroke(width = 1.0.dp.toPx())
            )
        }
    }
}
