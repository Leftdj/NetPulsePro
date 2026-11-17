package com.flowstats.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowstats.app.data.AppUsage
import com.flowstats.app.ui.AppDetailScreen
import com.flowstats.app.ui.AppsScreen
import com.flowstats.app.ui.InsightsScreen
import com.flowstats.app.ui.MainViewModel
import com.flowstats.app.ui.NetworkScreen
import com.flowstats.app.ui.OverviewScreen
import com.flowstats.app.ui.SettingsScreen
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.FlowStatsTheme
import com.flowstats.app.ui.theme.HeroColors
import com.flowstats.app.ui.theme.Purple

private sealed interface Screen {
    data object Overview : Screen
    data object Flow : Screen
    data object Apps : Screen
    data object Network : Screen
    data object Settings : Screen
    data class Detail(val app: AppUsage) : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            FlowStatsTheme(darkTheme = ui.darkTheme) {
                var screen: Screen by remember { mutableStateOf<Screen>(Screen.Overview) }
                var detailFrom: Screen by remember { mutableStateOf<Screen>(Screen.Apps) }
                var drawerOpen by remember { mutableStateOf(false) }
                var lastBackPress by remember { mutableStateOf(0L) }
                val context = LocalContext.current

                // 逐层返回：抽屉 → 详情 → 回到总览 → 再按一次退出
                BackHandler {
                    when {
                        drawerOpen -> drawerOpen = false
                        screen is Screen.Detail -> screen = detailFrom
                        screen !is Screen.Overview -> screen = Screen.Overview
                        else -> {
                            val now = System.currentTimeMillis()
                            if (now - lastBackPress < 2000) finish()
                            else {
                                lastBackPress = now
                                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val usageLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { viewModel.checkPermission() }

                fun openUsageSettings() {
                    try {
                        usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                    }
                }

                // 应用详情页右上角三点：直达该应用的系统信息页（强制停止 / 卸载 / 流量 / 权限）
                fun openAppInfo(packageName: String) {
                    if (packageName.startsWith("uid-")) {
                        Toast.makeText(context, "该记录无法映射到已安装应用", Toast.LENGTH_SHORT).show()
                        return
                    }
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:$packageName"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        Toast.makeText(context, "无法打开应用信息", Toast.LENGTH_SHORT).show()
                    }
                }

                // Wi-Fi 名称 / 信号 / 频宽：Android 10+ 需定位权限，13+ 读扫描结果还需附近设备权限
                val wifiPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    if (!granted) {
                        Toast.makeText(
                            context,
                            "未授予权限，Wi-Fi 名称与信号强度将无法显示",
                            Toast.LENGTH_LONG
                        ).show()
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                }

                fun requestWifiPermissions() {
                    val perms = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            add("android.permission.NEARBY_WIFI_DEVICES")
                        }
                    }.toTypedArray()
                    wifiPermLauncher.launch(perms)
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> viewModel.checkPermission()
                            // 回到前台才开始自动测延迟/抖动/丢包，退出时停掉
                            Lifecycle.Event.ON_START -> viewModel.setForeground(true)
                            Lifecycle.Event.ON_STOP -> viewModel.setForeground(false)
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // 进入网络页时开启 2 秒自动刷新；离开时恢复默认轮询节奏
                LaunchedEffect(screen) {
                    val onNetwork = screen is Screen.Network
                    viewModel.setNetworkVisible(onNetwork)
                    if (onNetwork) viewModel.onNetworkVisible()
                }

                val surfaceColor = MaterialTheme.colorScheme.surface
                LaunchedEffect(screen, ui.darkTheme, surfaceColor) {
                    val heroHeader = screen is Screen.Overview || screen is Screen.Detail
                    @Suppress("DEPRECATION")
                    window.statusBarColor = if (heroHeader) {
                        HeroColors.first().toArgb()
                    } else {
                        surfaceColor.toArgb()
                    }
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        ?.isAppearanceLightStatusBars = !heroHeader && !ui.darkTheme
                }

                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = {
                            (fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 12 }) togetherWith
                                (fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { -it / 12 })
                        },
                        label = "screen"
                    ) { current ->
                        when (current) {
                            Screen.Overview -> OverviewScreen(
                                state = ui,
                                drawerOpen = drawerOpen,
                                onMenu = { drawerOpen = true },
                                onOpenNetwork = { screen = Screen.Network },
                                onOpenUsageSettings = { openUsageSettings() }
                            )
                            Screen.Flow -> InsightsScreen(
                                state = ui,
                                onMenu = { drawerOpen = true },
                                onOpenApp = { detailFrom = Screen.Flow; screen = Screen.Detail(it) },
                                onOpenSettings = { openUsageSettings() }
                            )
                            Screen.Apps -> AppsScreen(
                                state = ui,
                                onMenu = { drawerOpen = true },
                                onOpenSettings = { openUsageSettings() },
                                onPeriod = { viewModel.setPeriod(it) },
                                onShiftDate = { viewModel.shiftDate(it) },
                                onPickDate = { y, m, d -> viewModel.pickDate(y, m, d) },
                                onCalendarMonth = { y, m -> viewModel.loadMonthHeat(y, m) },
                                onOpenApp = { detailFrom = Screen.Apps; screen = Screen.Detail(it) },
                                onQuery = { viewModel.setQuery(it) },
                                onSort = { viewModel.setSort(it) },
                                onFilter = { viewModel.setFilter(it) },
                                onRefresh = { viewModel.reload() }
                            )
                            Screen.Network -> NetworkScreen(
                                state = ui,
                                onRunDiagnostics = { viewModel.runDiagnostics() },
                                onClearEvents = { viewModel.clearEvents() },
                                onOpenLocationSettings = { requestWifiPermissions() },
                                onRefreshPublicIp = { viewModel.refreshPublicIp(force = true) },
                                onRunDnsCompare = { viewModel.runDnsCompare() },
                                onClearLinkHistory = { viewModel.clearLinkHistory() }
                            )
                            Screen.Settings -> SettingsScreen(
                                state = ui,
                                onMenu = { drawerOpen = true },
                                onOpenUsageSettings = { openUsageSettings() },
                                onToggleTheme = { viewModel.toggleTheme() },
                                onRefreshHz = { viewModel.setRefreshHz(it) },
                                onToggleSystem = { viewModel.toggleSystem() },
                                onToggleHideUnknown = { viewModel.toggleHideUnknown() },
                                onSetQuota = { viewModel.setQuotaGb(it) }
                            )
                            is Screen.Detail -> AppDetailScreen(
                                app = current.app,
                                dateLabel = ui.dateLabel,
                                dailyTrend = ui.dailyTrend,
                                onBack = { screen = detailFrom },
                                onAction = { openAppInfo(current.app.packageName) }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = screen !is Screen.Detail,
                        enter = slideInVertically(tween(300)) { it },
                        exit = slideOutVertically(tween(250)) { it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    ) {
                        BottomNav(current = screen) { target ->
                            screen = target
                            drawerOpen = false
                        }
                    }

                    // 抽屉覆盖层：遮罩与面板共用一个进度值，保证严格同步、不再出现遮罩丢失
                    val drawerProgress by animateFloatAsState(
                        targetValue = if (drawerOpen) 1f else 0f,
                        animationSpec = if (drawerOpen) {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        } else {
                            tween(240, easing = FastOutSlowInEasing)
                        },
                        label = "drawerProgress"
                    )
                    if (drawerProgress > 0.001f) {
                        val drawerWidthPx = with(LocalDensity.current) { 272.dp.toPx() }
                        Box(Modifier.fillMaxSize()) {
                            // 遮罩：alpha 直接由进度驱动，必定绘制
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f * drawerProgress))
                                    .clickable(enabled = drawerOpen) { drawerOpen = false }
                            )
                            // 抽屉面板：X 位移由进度驱动
                            Column(
                                Modifier.width(272.dp).fillMaxHeight()
                                    .graphicsLayer {
                                        translationX = (drawerProgress - 1f) * drawerWidthPx
                                    }
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().height(152.dp)
                                        .background(Brush.linearGradient(HeroColors))
                                        .padding(20.dp),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    Column {
                                        Text(
                                            "NetPulse Pro",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "专业网络监控与诊断",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.5.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                DrawerItem("总览", screen is Screen.Overview) { screen = Screen.Overview; drawerOpen = false }
                                DrawerItem("流量", screen is Screen.Flow) { screen = Screen.Flow; drawerOpen = false }
                                DrawerItem("应用", screen is Screen.Apps || screen is Screen.Detail) { screen = Screen.Apps; drawerOpen = false }
                                DrawerItem("网络", screen is Screen.Network) { screen = Screen.Network; drawerOpen = false }
                                DrawerItem("设置", screen is Screen.Settings) { screen = Screen.Settings; drawerOpen = false }
                                DrawerItem("使用权限", false) { drawerOpen = false; openUsageSettings() }
                                Text(
                                    "实时速度 / Wi-Fi 链路 / 网络诊断\n应用流量按移动与 Wi-Fi 分开统计\n周期：日 / 周 / 月 / 年",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(current: Screen, onSelect: (Screen) -> Unit) {
    val items = listOf(
        Triple("总览", "◎", Screen.Overview),
        Triple("流量", "▤", Screen.Flow),
        Triple("应用", "▦", Screen.Apps),
        Triple("网络", "⌁", Screen.Network),
        Triple("设置", "⚙", Screen.Settings)
    )
    Row(
        Modifier.fillMaxWidth().height(78.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 12.dp)
    ) {
        items.forEach { (label, icon, target) ->
            val on = current == target
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(target) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (on) Blue.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    Text(
                        icon,
                        fontSize = 18.sp,
                        color = if (on) Blue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    label,
                    fontSize = 9.5.sp,
                    color = if (on) Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp)
            .background(if (selected) Blue.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(3.dp))
                .background(if (selected) Purple else MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = if (selected) Blue else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp
        )
    }
}
