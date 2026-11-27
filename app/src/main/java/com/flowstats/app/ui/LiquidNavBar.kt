package com.flowstats.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.min

/**
 * 液态玻璃底部导航栏。
 *
 * 四种动画（对应参考图）：
 * 1. Squash 按下压扁：指腹按下时胶囊横向 +6%、纵向 -16%，中心不动；
 * 2. Spring 松手回弹：spring(dampingRatio 0.42) 带轻微过冲，不是线性位移；
 * 3. Merge  靠近融合：胶囊在旧位置与新位置之间拉出带「腰」的贝塞尔形状，离得越远腰越细，
 *    到位后自动退化成标准胶囊 —— 一个形状在流动，不是两个方块交叉；
 * 4. Light  移动流光：一段 62% 宽的白色横向渐变沿玻璃表面掠过，sin 淡入淡出，结束后归于平静。
 *
 * 交互：
 * - 单击选中（松手时判定，按下阶段不动选中项，避免滑动页面时误切）；
 * - 长按 0.5s 后左右拖动连续切换，带触感反馈，两端有橡皮筋；
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
    val pillX = remember { Animatable(0f) }
    val squash = remember { Animatable(0f) }
    val sheen = remember { Animatable(-1f) }
    // 移动过程中的横向拉伸量：先胀后缩，形成液体被带着走的形变
    val stretch = remember { Animatable(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val cellW = remember { mutableFloatStateOf(0f) }
    val indexNow = rememberUpdatedState(selectedIndex)

    val springMove: AnimationSpec<Float> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        spring(dampingRatio = 0.62f, stiffness = 260f)
    }

    // 选中项变化 → 胶囊用弹簧滑到新格；同时放一次流光
    LaunchedEffect(selectedIndex, cellW.floatValue, dragging) {
        if (!dragging && cellW.floatValue > 0f) {
            pillX.animateTo(selectedIndex * cellW.floatValue, springMove)
        }
    }
    LaunchedEffect(selectedIndex) {
        if (reduceMotion) {
            stretch.snapTo(0f)
            return@LaunchedEffect
        }
        launch {
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(durationMillis = 620, easing = LinearEasing))
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
                    var longPressed = false
                    var moved = false
                    var lastX = startX
                    scope.launch {
                        squash.animateTo(
                            if (reduceMotion) 0f else 1f,
                            spring(dampingRatio = 0.55f, stiffness = 1400f)
                        )
                    }
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
                                dragX = if (reduceMotion) indexNow.value * cellW.floatValue
                                else pillX.value
                                lastX = change.position.x
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        } else {
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            if (dx != 0f) {
                                change.consume()
                                val idx = indexNow.value
                                val w = cellW.floatValue
                                val raw = dragX + dx
                                val base = idx * w
                                val step = w * 0.5f
                                val next = when {
                                    raw - base > step && idx < labels.lastIndex -> idx + 1
                                    raw - base < -step && idx > 0 -> idx - 1
                                    else -> idx
                                }
                                if (next != idx) {
                                    onSelect(next)
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragX = raw.coerceIn(-w * 0.18f, labels.lastIndex * w + w * 0.18f)
                            }
                        }
                    }
                    // 松手·回弹
                    scope.launch {
                        squash.animateTo(
                            0f,
                            if (reduceMotion) tween(0)
                            else spring(dampingRatio = 0.42f, stiffness = 380f)
                        )
                    }
                    if (longPressed) {
                        scope.launch {
                            pillX.snapTo(dragX)
                            dragging = false
                        }
                    } else if (!moved && cellW.floatValue > 0f) {
                        // 单击：按下不选中，松手才判定，避免拖动内容时误切页面
                        val idx = (startX / cellW.floatValue).toInt().coerceIn(0, labels.lastIndex)
                        if (idx != indexNow.value) onSelect(idx)
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val itemW = widthPx / labels.size
        SideEffect { cellW.floatValue = itemW }
        val selected by rememberUpdatedState(selectedIndex)

        // 玻璃本体由 liquidGlass 绘制在这层之下；这里只画选中胶囊，保证它在图标之下
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / labels.size
            val target = selected * cell
            val current = if (dragging) dragX else pillX.value
            val pad = 11.dp.toPx()
            val inset = 5.dp.toPx()
            // 拖动时：形状从手指位置一直桥接到目标格，两格之间被拉出细腰
            // 点击时：胶囊随身移动，同时横向胀开再收拢（液体被带走）
            val left: Float
            val right: Float
            if (dragging) {
                left = min(current, target)
                right = maxOf(current, target) + cell
            } else {
                val grow = cell * 0.55f * stretch.value
                left = current - grow * 0.5f
                right = current + cell + grow * 0.5f
            }
            drawLiquidPill(
                left = left + inset,
                right = right - inset,
                top = pad,
                bottom = size.height - pad,
                squash = squash.value,
                palette = palette,
                sheen = sheen.value
            )
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
                    targetValue = if (on) 1f else 0.93f,
                    animationSpec = if (reduceMotion) tween(0)
                    else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
    }
}
