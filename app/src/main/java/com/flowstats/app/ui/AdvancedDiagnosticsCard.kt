package com.flowstats.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowstats.app.data.AdvancedChecks
import com.flowstats.app.data.CheckItem
import com.flowstats.app.data.CheckStatus
import com.flowstats.app.data.TargetResult
import com.flowstats.app.data.TargetSpec
import com.flowstats.app.ui.theme.Blue
import com.flowstats.app.ui.theme.Danger
import com.flowstats.app.ui.theme.Success
import com.flowstats.app.ui.theme.Warning

/** 一键体检 + 多目标网络监控 */
@Composable
fun AdvancedDiagnosticsCard(
    checks: List<CheckItem>,
    checking: Boolean,
    targets: List<TargetSpec>,
    results: List<TargetResult>,
    targetChecking: Boolean,
    onRunChecks: () -> Unit,
    onRunTargets: () -> Unit,
    onAddTarget: (String, String) -> Unit,
    onRemoveTarget: (String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    SoftCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, corner = 22.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("一键体检", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    if (checking) "检查中…" else "开始检查",
                    fontSize = 11.sp, color = Blue, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !checking) { onRunChecks() }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            if (checks.isEmpty()) {
                Text("点「开始检查」核对连接、DNS、IPv4/IPv6、网关、延迟、丢包与 MTU。",
                    fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                checks.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(statusColor(item.status)))
                        Spacer(Modifier.width(7.dp))
                        Text(item.label, fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(58.dp))
                        Text(item.detail, fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("未测量项显示「未测量 / 系统未上报」，不会用估算值补齐。",
                    fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("多目标监控", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    if (targetChecking) "检测中…" else "检测全部",
                    fontSize = 11.sp, color = Blue, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = !targetChecking) { onRunTargets() }.padding(4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            targets.forEach { t ->
                val r = results.firstOrNull { it.host == t.host }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (r == null) Color.Gray else if (r.ok) Success else Danger))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(t.host, fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        when {
                            r == null -> "未检测"
                            r.ok -> "${r.rttAvg?.toInt() ?: "—"} ms · 丢包 ${r.lossPct ?: "—"}%"
                            else -> "不可达"
                        },
                        fontSize = 9.5.sp,
                        color = if (r == null) MaterialTheme.colorScheme.onSurfaceVariant else if (r.ok) Success else Danger
                    )
                    if (!t.builtIn) {
                        Text("×", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onRemoveTarget(t.host) }.padding(start = 7.dp))
                    }
                }
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    AdvancedChecks.locateFault(results),
                    fontSize = 10.sp,
                    color = if (results.all { it.ok }) Success else Warning,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = label, onValueChange = { if (it.length <= 8) label = it },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    textStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        if (label.isEmpty()) Text("名称", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = host, onValueChange = { if (it.length <= 64) host = it },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    textStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner ->
                        if (host.isEmpty()) Text("域名或 IP", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                )
                Spacer(Modifier.width(6.dp))
                Text("添加", fontSize = 10.5.sp, color = Blue,
                    modifier = Modifier.clickable(enabled = host.isNotBlank()) {
                        onAddTarget(label.trim(), host.trim())
                        label = ""; host = ""
                    }.padding(4.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "ICMP 可能被目标服务器禁ping，不可达不等于服务故障；结论只用于缩小排查范围。",
                fontSize = 9.sp, lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun statusColor(status: CheckStatus): Color = when (status) {
    CheckStatus.OK -> Success
    CheckStatus.WARN -> Warning
    CheckStatus.FAIL -> Danger
    CheckStatus.UNKNOWN -> Color.Gray
}
