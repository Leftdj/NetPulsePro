package com.flowstats.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 一次“检测”的真实快照（区别于每 2 秒一条的链路采样）。
 * 来源：手动“重新测量”，以及网络页前台时每 5 分钟一次的自动快照。
 * 所有数值均为实测，未测到的字段为 null。
 */
data class DiagnosticRecord(
    val time: Long,
    val host: String,
    val rttAvg: Double?,
    val jitter: Double?,
    val lossPercent: Int?,
    val ok: Boolean,
    val dnsServer: String?,
    val dnsMs: Double?,
    val rssi: Int?,
    val linkMbps: Int?,
    val isWifi: Boolean
) {
    val verdict: String
        get() = when {
            !ok -> "测量失败"
            lossPercent != null && lossPercent >= 5 -> "丢包偏高"
            rttAvg != null && rttAvg >= 150 -> "延迟偏高"
            jitter != null && jitter >= 50 -> "抖动偏大"
            else -> "正常"
        }
}

/** 检测历史：本地持久化，按今天/昨天/本周限量保留 */
object DiagnosticHistory {

    private const val PREF = "diagnostic_history"
    private const val KEY = "records"
    // 每 1 秒自动记录一条，容量需覆盖约 5 分钟；写入用 apply 异步，不阻塞主线程
    private const val MAX_TODAY = 300
    private const val MAX_YESTERDAY = 300
    private const val MAX_WEEK = 1000

    @Volatile
    private var cached: List<DiagnosticRecord>? = null

    fun load(context: Context): List<DiagnosticRecord> {
        cached?.let { return it }
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null)
        val list: List<DiagnosticRecord> = if (raw == null) emptyList()
        else runCatching { parse(raw) }.getOrElse { emptyList() }
        cached = list
        return list
    }

    @Synchronized
    fun add(context: Context, record: DiagnosticRecord): List<DiagnosticRecord> {
        val current = load(context).toMutableList()
        current.add(0, record)
        val pruned = prune(current)
        cached = pruned
        persist(context.applicationContext, pruned)
        return pruned
    }

    @Synchronized
    fun clear(context: Context) {
        cached = emptyList()
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    /** 按本地日历日分组，最新在前 */
    fun groupByDay(records: List<DiagnosticRecord>): List<Pair<String, List<DiagnosticRecord>>> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val label = SimpleDateFormat("M月d日", Locale.CHINA)
        return records.groupBy { fmt.format(java.util.Date(it.time)) }
            .toList().sortedByDescending { it.first }
            .map { (key, list) ->
                val dayLabel = runCatching {
                    label.format(SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(key)!!)
                }.getOrDefault(key)
                dayLabel + (todayLabel(key)?.let { " · $it" } ?: "") to list
            }
    }

    fun stats(records: List<DiagnosticRecord>): Counts {
        val today = dayKey(0)
        val yesterday = dayKey(-1)
        val weekStart = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
        }.timeInMillis
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        return Counts(
            today = records.count { fmt.format(java.util.Date(it.time)) == today },
            yesterday = records.count { fmt.format(java.util.Date(it.time)) == yesterday },
            week = records.count { it.time >= weekStart }
        )
    }

    data class Counts(val today: Int, val yesterday: Int, val week: Int)

    private fun todayLabel(key: String): String? = when (key) {
        dayKey(0) -> "今天"
        dayKey(-1) -> "昨天"
        else -> null
    }

    private fun dayKey(offsetDays: Int): String {
        val cal = Calendar.getInstance(Locale.CHINA)
        cal.add(Calendar.DAY_OF_MONTH, offsetDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.time)
    }

    /** 今天最多 30、昨天最多 30、本周最多 100，更早的丢弃 */
    private fun prune(list: List<DiagnosticRecord>): List<DiagnosticRecord> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        val today = dayKey(0)
        val yesterday = dayKey(-1)
        val weekStart = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
        }.timeInMillis
        var todayCount = 0
        var yesterdayCount = 0
        var weekCount = 0
        val result = mutableListOf<DiagnosticRecord>()
        for (r in list) { // list 已按最新在前
            val key = fmt.format(java.util.Date(r.time))
            when {
                key == today -> { if (todayCount++ < MAX_TODAY) result += r }
                key == yesterday -> { if (yesterdayCount++ < MAX_YESTERDAY) result += r }
                r.time >= weekStart -> { if (weekCount++ < MAX_WEEK) result += r }
                // 更早的记录丢弃
            }
        }
        return result
    }

    private fun persist(context: Context, list: List<DiagnosticRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().apply {
                put("t", r.time)
                put("h", r.host)
                putNullIf("a", r.rttAvg)
                putNullIf("j", r.jitter)
                putNullIf("l", r.lossPercent)
                put("k", r.ok)
                put("s", r.dnsServer ?: JSONObject.NULL)
                putNullIf("d", r.dnsMs)
                putNullIf("r", r.rssi)
                putNullIf("m", r.linkMbps)
                put("w", r.isWifi)
            })
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    private fun JSONObject.putNullIf(key: String, value: Number?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun parse(raw: String): List<DiagnosticRecord> {
        val arr = JSONArray(raw)
        val result = mutableListOf<DiagnosticRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result += DiagnosticRecord(
                time = o.optLong("t"),
                host = o.optString("h"),
                rttAvg = optDoubleOrNull(o, "a"),
                jitter = optDoubleOrNull(o, "j"),
                lossPercent = optIntOrNull(o, "l"),
                ok = o.optBoolean("k"),
                dnsServer = if (o.isNull("s")) null else o.optString("s"),
                dnsMs = optDoubleOrNull(o, "d"),
                rssi = optIntOrNull(o, "r"),
                linkMbps = optIntOrNull(o, "m"),
                isWifi = o.optBoolean("w", true)
            )
        }
        return result
    }

    private fun optIntOrNull(o: JSONObject, key: String): Int? =
        if (o.isNull(key)) null else o.optInt(key)

    private fun optDoubleOrNull(o: JSONObject, key: String): Double? =
        if (o.isNull(key)) null else o.optDouble(key)
}
