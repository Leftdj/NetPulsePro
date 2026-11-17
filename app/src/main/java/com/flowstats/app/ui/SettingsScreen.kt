package com.flowstats.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri
import com.flowstats.app.data.AppUpdate
import com.flowstats.app.data.UpdateResult
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.HeroColors
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Slate
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Teal

@Composable
fun SettingsScreen(
    state: UiState,
    onMenu: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onRefreshHz: (Int) -> Unit,
    onToggleSystem: () -> Unit,
    onToggleHideUnknown: () -> Unit,
    onSetQuota: (Int) -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "3.1"
    }
    val versionCode = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
    }
    var enterTarget by remember { mutableStateOf(0f) }
    var quotaOpen by remember { mutableStateOf(false) }
    var versionOpen by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { enterTarget = 1f }
    val enter by animateFloatAsState(enterTarget, tween(420), label = "settingsEnter")
    Column(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = enter; translationY = (1f - enter) * 12f }
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        TopBar("设置", onMenu = onMenu, onAction = onOpenUsageSettings)
        Column(Modifier.padding(14.dp)) {
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                            .background(Brush.linearGradient(HeroColors)),
                        contentAlignment = Alignment.Center
                    ) { Text("▣", color = Color.White, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                    Text("NetPulse Pro", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("版本 $versionName · 专业网络监控与诊断", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp))
                }
                Text("版本信息 ›", fontSize = 10.5.sp, color = Blue, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        updateResult = null
                        versionOpen = true
                    })
            }
            }

            Spacer(Modifier.height(14.dp))
            Text("偏好设置", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingRow(
                        "外观主题", if (state.darkTheme) "深色护眼 · 已开启" else "浅色清爽 · 已关闭",
                        glyph = "◐", glyphColor = Purple,
                        trailing = { AnimatedSwitch(state.darkTheme, onToggleTheme, Purple) }
                    )
                    SettingDivider()
                    SettingRow("刷新频率", "实时网速采样间隔", glyph = "↻", glyphColor = Teal, trailing = {
                        RefreshSegments(state.refreshHz, onRefreshHz)
                    })
                    SettingDivider()
                    SettingRow("显示系统应用", "包含无图标的系统服务", glyph = "◈", glyphColor = Blue,
                        trailing = { AnimatedSwitch(state.showSystem, onToggleSystem, Blue) })
                    SettingDivider()
                    SettingRow("隐藏未知应用", "已卸载残留、无法解析的进程", glyph = "?", glyphColor = Slate,
                        trailing = { AnimatedSwitch(state.hideUnknown, onToggleHideUnknown, Slate) })
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("数据", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingRow("权限管理", "使用情况访问权限", glyph = "✓", glyphColor = Success, trailing = {
                        Text("打开 ›", color = Blue, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onOpenUsageSettings))
                    })
                    SettingDivider()
                    SettingRow("统计维度", "移动 / WiFi · 下载 / 上传", glyph = "▤", glyphColor = Orange, trailing = {})
                    SettingDivider()
                    SettingRow("月度套餐", "本月已用 ${formatBytes(state.month.total)}", glyph = "◎", glyphColor = Blue,
                        trailing = {
                            Row(
                                Modifier.clip(RoundedCornerShape(9.dp))
                                    .background(Blue.copy(alpha = 0.10f))
                                    .clickable { quotaOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (state.monthlyQuotaGb > 0) "${state.monthlyQuotaGb} GB" else "未设置",
                                    fontSize = 11.sp, color = Blue, fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("›", fontSize = 13.sp, color = Blue)
                            }
                        })
                    SettingDivider()
                    SettingRow("版本信息", "更新日志 · 检测更新 · 开源地址", glyph = "ⓘ", glyphColor = Purple,
                        trailing = {
                            Text("查看 ›", fontSize = 11.sp, color = Blue,
                                modifier = Modifier.clickable {
                                    updateResult = null
                                    versionOpen = true
                                })
                        })
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("支持", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
            SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column {
                    SettingRow("开源项目", "Leftdj/NetPulsePro · GitHub", glyph = "⌂", glyphColor = Teal,
                        trailing = {
                            Text("打开 ›", fontSize = 11.sp, color = Blue,
                                modifier = Modifier.clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdate.GITHUB_URL)))
                                    }
                                })
                        })
                }
            }

            Spacer(Modifier.height(26.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NetPulse Pro · 数据来自系统 NetworkStatsManager", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                Spacer(Modifier.height(9.dp))
                Text("© 2026 NetPulse Pro", fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    letterSpacing = 0.4.sp)
            }
            Spacer(Modifier.height(90.dp))
        }
    }

    if (quotaOpen) {
        QuotaDialog(
            currentGb = state.monthlyQuotaGb,
            usedBytes = state.month.total,
            onDismiss = { quotaOpen = false },
            onConfirm = { gb -> onSetQuota(gb); quotaOpen = false }
        )
    }

    if (versionOpen) {
        VersionInfoDialog(
            versionName = versionName,
            checking = checking,
            result = updateResult,
            onDismiss = { versionOpen = false },
            onCheck = {
                checking = true
                updateResult = null
                scope.launch {
                    updateResult = AppUpdate.check(versionCode)
                    checking = false
                }
            },
            onOpenGithub = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdate.GITHUB_URL)))
                }
            },
            onDownload = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    sub: String,
    glyph: String? = null,
    glyphColor: Color = Blue,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                    .background(glyphColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glyphColor)
            }
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            if (sub.isNotEmpty()) Text(
                sub, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun SettingDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 55.dp).height(1.dp)
        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
}

/**
 * 参考样式的开关：大滑块 + 同色半透明轨道 + 阴影。
 * 滑块沿轨道平滑滑动，颜色渐变，按下时有轻微放大反馈。
 */
@Composable
private fun AnimatedSwitch(checked: Boolean, onToggle: () -> Unit, accent: Color = Blue) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "switchThumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) accent.copy(alpha = 0.42f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
        animationSpec = tween(260),
        label = "switchTrack"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) accent else Color(0xFFF7F9FC),
        animationSpec = tween(260),
        label = "switchThumbColor"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.12f else 1f,
        animationSpec = tween(120),
        label = "switchPress"
    )

    Box(
        modifier = Modifier
            .width(54.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .toggleable(
                value = checked,
                role = Role.Switch,
                interactionSource = interaction,
                onValueChange = { onToggle() }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.padding(start = 3.dp)
                .offset(x = thumbX)
                .size(26.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

/** 分段选择器：选中底色会平滑滑动到目标项。 */
@Composable
private fun RefreshSegments(current: Int, onPick: (Int) -> Unit) {
    val values = listOf(500, 1000, 2000)
    val labels = listOf("0.5s", "1s", "2s")
    val index = values.indexOf(current).coerceAtLeast(0)
    val x by animateDpAsState(
        targetValue = (index * 42).dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "segmentIndicator"
    )
    Box(
        Modifier.width(126.dp).height(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.offset(x = x).padding(2.dp)
                .size(width = 42.dp, height = 30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.horizontalGradient(listOf(Blue, Purple)))
        )
        Row {
            values.forEachIndexed { i, hz ->
                Box(
                    Modifier.width(42.dp).height(30.dp).clickable { onPick(hz) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        labels[i], fontSize = 10.sp,
                        color = if (i == index) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 月度套餐额度：常用档位 + 自定义输入。 */
@Composable
private fun QuotaDialog(
    currentGb: Int,
    usedBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember { mutableStateOf(currentGb.toString()) }
    val parsed = input.trim().toIntOrNull()
    val valid = parsed != null && parsed in 1..9999
    val presets = listOf(5, 10, 20, 30, 50, 100, 200, 300, 500)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null, onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null, onClick = {}
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(14.dp))
                Text("月度套餐", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "本月已用 ${formatBytes(usedBytes)}，额度用于总览页的进度环与超额提醒",
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(14.dp))

                presets.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { gb ->
                            val active = parsed == gb
                            Box(
                                Modifier.weight(1f).height(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (active) Brush.horizontalGradient(listOf(Blue, Purple))
                                        else Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                    )
                                    .clickable { input = gb.toString() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (gb >= 100) "${gb} GB" else "${gb} GB",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text("自定义", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth().height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { text -> input = text.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = Brush.horizontalGradient(listOf(Blue, Purple)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text("输入额度", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                            inner()
                        }
                    )
                    Text("GB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onDismiss() }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("取消", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                            .background(
                                if (valid) Brush.horizontalGradient(listOf(Blue, Purple))
                                else Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                            .clickable(enabled = valid) { parsed?.let(onConfirm) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (valid) "保存 ${parsed} GB" else "保存",
                            fontSize = 13.sp,
                            color = if (valid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
