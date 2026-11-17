package com.flowstats.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 流量报表 CSV 导出。三份独立文件：日流量、应用排行、时段分布。
 * 数据全部来自 UsageRepository 的真实查询，不做任何估算或伪造。
 */
object CsvExporter {

    data class ExportSummary(val files: Int, val location: String)

    /** 生成并写入下载目录，返回写入结果描述 */
    fun export(context: Context, repo: UsageRepository, period: Period, offset: Int): ExportSummary {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(java.util.Date())
        val dateLabel = repo.dateLabel(period, offset).replace(Regex("[年月/\\s]"), "-").trim('-')

        val daily = buildDailyCsv(repo)
        val apps = buildAppsCsv(repo, period, offset)
        val hourly = buildHourlyCsv(repo)

        val specs = listOf(
            "NetPulse_日流量_${stamp}.csv" to daily,
            "NetPulse_应用排行_${dateLabel}_${stamp}.csv" to apps,
            "NetPulse_时段分布_${stamp}.csv" to hourly
        )
        var location = ""
        specs.forEach { (name, content) ->
            location = writeToDownloads(context, name, content)
        }
        return ExportSummary(specs.size, location)
    }

    /** 最近 30 天每日流量 */
    private fun buildDailyCsv(repo: UsageRepository): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM，Excel 正确识别中文
        sb.append("日期,移动下载(MB),移动上传(MB),WiFi下载(MB),WiFi上传(MB),合计(MB),移动合计(MB),WiFi合计(MB)\n")
        val rows = repo.dailyTotals(30)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        rows.forEachIndexed { i, (t, _) ->
            val cal = Calendar.getInstance(Locale.CHINA)
            cal.add(Calendar.DAY_OF_MONTH, -(rows.size - 1 - i))
            sb.append(listOf(
                fmt.format(cal.time),
                mb(t.mobileRx), mb(t.mobileTx), mb(t.wifiRx), mb(t.wifiTx),
                mb(t.total), mb(t.mobile), mb(t.wifi)
            ).joinToString(",")).append('\n')
        }
        return sb.toString()
    }

    /** 当前周期应用排行 */
    private fun buildAppsCsv(repo: UsageRepository, period: Period, offset: Int): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("周期,${escape(repo.dateLabel(period, offset))}\n\n")
        sb.append("排名,应用名称,包名,类型,移动下载(MB),移动上传(MB),WiFi下载(MB),WiFi上传(MB),合计(MB)\n")
        val (start, end) = repo.rangeFor(period, offset)
        val (apps, _) = repo.loadAppUsages(start, end)
        if (apps.isEmpty()) {
            sb.append("（当前周期无应用流量数据，或尚未授予使用权限）\n")
        } else {
            apps.forEachIndexed { i, a ->
                sb.append(listOf(
                    (i + 1).toString(),
                    escape(a.label),
                    escape(if (a.isUnknown) "未知应用(UID ${a.uid})" else a.packageName),
                    if (a.isSystem) "系统" else "用户",
                    mb(a.mobileRx), mb(a.mobileTx), mb(a.wifiRx), mb(a.wifiTx), mb(a.total)
                ).joinToString(",")).append('\n')
            }
        }
        return sb.toString()
    }

    /** 今天 24 小时时段分布 */
    private fun buildHourlyCsv(repo: UsageRepository): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("日期,${SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(java.util.Date())}\n\n")
        sb.append("时段,移动(MB),WiFi(MB),合计(MB)\n")
        repo.hourlyTotals().forEachIndexed { hour, (mobile, wifi) ->
            sb.append(listOf(
                "%02d:00-%02d:00".format(hour, hour),
                mb(mobile), mb(wifi), mb(mobile + wifi)
            ).joinToString(",")).append('\n')
        }
        return sb.toString()
    }

    private fun mb(bytes: Long): String = String.format(Locale.CHINA, "%.2f", bytes / 1048576.0)

    private fun escape(s: String): String {
        val v = s.replace("\"", "\"\"")
        return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"$v\"" else v
    }

    /** 导出运行日志（时间 / 级别 / 分类 / 内容） */
    fun exportLogs(context: Context, events: List<NetEvent>, onResult: (String) -> Unit) {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("NetPulse Pro 运行日志\n")
        sb.append("导出时间,${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(java.util.Date())}\n")
        sb.append("记录条数,${events.size}\n\n")
        sb.append("时间,级别,分类,内容\n")
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        events.forEach { e ->
            sb.append(listOf(
                ts.format(java.util.Date(e.time)),
                e.level.label,
                e.kind.name,
                escape(e.detail)
            ).joinToString(",")).append('\n')
        }
        val name = "NetPulse_运行日志_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(java.util.Date())}.csv"
        val location = writeToDownloads(context, name, sb.toString())
        onResult(location)
    }

    /** Android 10+ 用 MediaStore 写公共下载目录；低版本写应用外部目录，均无需存储权限 */
    private fun writeToDownloads(context: Context, fileName: String, content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri? = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return "下载目录"
            }
        }
        // 兜底：应用专属外部目录（无需权限），低版本也可用
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
