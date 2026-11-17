package com.flowstats.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowstats.app.data.AppUpdate
import com.flowstats.app.data.UpdateResult
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.Orange
import com.flowstats.app.ui.theme.Purple
import com.flowstats.app.ui.theme.Success

/**
 * 版本信息弹窗：当前版本 + 更新日志 + 检测更新 + 打开 GitHub。
 * 样式参考用户提供的截图（白底圆角卡片、蓝色版本标签、蓝色主按钮）。
 */
@Composable
fun VersionInfoDialog(
    versionName: String,
    checking: Boolean,
    result: UpdateResult?,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onOpenGithub: () -> Unit,
    onDownload: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 遮罩
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(initialScale = 0.92f, animationSpec = tween(260)) + fadeIn(tween(220)),
                exit = scaleOut(targetScale = 0.94f, animationSpec = tween(180)) + fadeOut(tween(160))
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 28.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        "版本信息",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(14.dp))

                    // 当前版本
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "当前版本",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(Blue.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("v$versionName", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "最近正式版本",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))

                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        AppUpdate.history.forEachIndexed { index, note ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                                Column(Modifier.width(72.dp)) {
                                    Text(
                                        note.version,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Blue
                                    )
                                    Text(
                                        "build ${note.build}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                                Text(
                                    note.notes,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (index != AppUpdate.history.lastIndex) {
                                Box(
                                    Modifier.fillMaxWidth().height(1.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                )
                            }
                        }
                    }

                    // 检测结果
                    AnimatedVisibility(visible = checking || result != null) {
                        when {
                            checking -> StatusLine("正在检测…", MaterialTheme.colorScheme.onSurfaceVariant)
                            result is UpdateResult.UpToDate -> StatusLine("✓ 已是最新版本", Success)
                            result is UpdateResult.Available -> {
                                StatusLine("发现新版本 v${result.version}（build ${result.code}）", Success)
                                if (result.notes.isNotBlank()) {
                                    Text(
                                        result.notes,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                }
                            }
                            result is UpdateResult.NoReleases -> StatusLine("下载站暂未配置版本清单", Orange)
                            result is UpdateResult.Error -> StatusLine("检测失败：${result.message}", Danger)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 主按钮：发现新版本时变为「立即下载」，否则为「检测更新」
                    val available = result as? UpdateResult.Available
                    Box(
                        Modifier.fillMaxWidth().height(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (checking) Brush.horizontalGradient(listOf(Blue.copy(alpha = 0.5f), Purple.copy(alpha = 0.5f)))
                                else Brush.horizontalGradient(listOf(Blue, Purple))
                            )
                            .clickable(enabled = !checking) {
                                if (available != null) onDownload(available.url) else onCheck()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (available != null) "↓" else "⇩",
                                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    checking -> "检测中…"
                                    available != null -> "立即下载 v${available.version}"
                                    else -> "检测更新"
                                },
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "打开 GitHub",
                            fontSize = 12.sp,
                            color = Blue,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { onOpenGithub() }.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                        Box(Modifier.size(1.dp, 12.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                        Text(
                            "关闭",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.5.sp,
        color = color,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}
