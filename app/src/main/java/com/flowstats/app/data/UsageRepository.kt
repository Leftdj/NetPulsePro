package com.flowstats.app.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min

class UsageRepository(private val context: Context) {

    private val statsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager: PackageManager = context.packageManager

    /** 需要用户在「设置 - 应用使用权限/usage access」中手动授予 */
    fun hasUsagePermission(): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        false
    }

    fun rangeFor(period: Period, offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
        }
        val endCal = Calendar.getInstance(Locale.CHINA)
        when (period) {
            Period.DAY -> {
                cal.add(Calendar.DAY_OF_MONTH, offset)
                endCal.timeInMillis = cal.timeInMillis
                endCal.add(Calendar.DAY_OF_MONTH, 1)
            }
            Period.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                endCal.timeInMillis = cal.timeInMillis
                endCal.add(Calendar.DAY_OF_MONTH, 7)
            }
            Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, offset)
                endCal.timeInMillis = cal.timeInMillis
                endCal.add(Calendar.MONTH, 1)
            }
            Period.YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.YEAR, offset)
                endCal.timeInMillis = cal.timeInMillis
                endCal.add(Calendar.YEAR, 1)
            }
        }
        val now = System.currentTimeMillis()
        return cal.timeInMillis to min(endCal.timeInMillis, now).coerceAtLeast(cal.timeInMillis)
    }

    /**
     * 把某个锚点日期换算成指定周期下的偏移量（0 = 当前周期，负数 = 过去）。
     * 关键：偏移量的单位随周期变化，切换周期时必须用锚点重新换算，
     * 不能直接把「周偏移 -24」当成「月偏移 -24」使用。
     */
    fun offsetFor(period: Period, anchorMillis: Long): Int {
        val today = Calendar.getInstance(Locale.CHINA).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
            firstDayOfWeek = Calendar.MONDAY
        }
        val anchor = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = anchorMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
            firstDayOfWeek = Calendar.MONDAY
        }
        val offset = when (period) {
            Period.DAY -> -((today.timeInMillis - anchor.timeInMillis) / DAY_MILLIS).toInt()
            Period.WEEK -> {
                anchor.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                today.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                -((today.timeInMillis - anchor.timeInMillis) / (DAY_MILLIS * 7)).toInt()
            }
            Period.MONTH ->
                -((today.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)) * 12 +
                    (today.get(Calendar.MONTH) - anchor.get(Calendar.MONTH)))
            Period.YEAR -> -(today.get(Calendar.YEAR) - anchor.get(Calendar.YEAR))
        }
        // 未来日期一律按当前周期处理
        return offset.coerceAtMost(0)
    }

    /** 指定日期（offset 天偏移）的 0 点到 24 点范围，不做 now 截断 */
    private fun fullDayRange(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
            add(Calendar.DAY_OF_MONTH, offset)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    fun dateLabel(period: Period, offset: Int): String {
        val cal = Calendar.getInstance(Locale.CHINA)
        cal.timeInMillis = rangeFor(period, offset).first
        return when (period) {
            Period.DAY -> SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(cal.time)
            Period.WEEK -> {
                // 区间是左闭右开的（end 为下周一 00:00），展示时要退一天，
                // 否则会出现「12月29日 - 1月5日」这种看起来有 8 天的周区间。
                val range = rangeFor(Period.WEEK, offset)
                val end = Calendar.getInstance(Locale.CHINA).apply {
                    timeInMillis = range.second
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(cal.time) + " - " +
                    SimpleDateFormat("M月d日", Locale.CHINA).format(end.time)
            }
            Period.MONTH -> SimpleDateFormat("yyyy年M月", Locale.CHINA).format(cal.time)
            Period.YEAR -> SimpleDateFormat("yyyy年", Locale.CHINA).format(cal.time)
        }
    }

    /** 设备级总流量（含系统），用于顶部汇总卡片 */
    fun totalsFor(start: Long, end: Long): Totals = Totals.fromPair(
        querySummary(TYPE_MOBILE, start, end),
        querySummary(TYPE_WIFI, start, end)
    )

    /** 按应用拆分：移动网络 / WiFi，各自下载(rx) / 上传(tx) */
    fun loadAppUsages(start: Long, end: Long): Pair<List<AppUsage>, Totals> {
        val mobileByUid = queryByUid(TYPE_MOBILE, start, end)
        val wifiByUid = queryByUid(TYPE_WIFI, start, end)

        val labels = mutableMapOf<Int, PackageInfoLite>()
        val packages = try {
            packageManager.getInstalledPackages(0)
        } catch (e: Exception) {
            emptyList()
        }
        for (info in packages) {
            val appInfo = info.applicationInfo ?: continue
            val uid = appInfo.uid
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (labels.containsKey(uid)) {
                // 同一 UID 下若有用户应用，优先展示用户应用
                if (isSystem) continue
            }
            val label = try {
                appInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                info.packageName
            }
            val icon = try {
                appInfo.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            }
            labels[uid] = PackageInfoLite(
                label = label,
                icon = icon,
                installed = info.firstInstallTime,
                updated = info.lastUpdateTime,
                permissions = info.requestedPermissions?.toList().orEmpty(),
                isSystem = isSystem
            )
        }

        val apps = (mobileByUid.keys + wifiByUid.keys).distinct().mapNotNull { uid ->
            val m = mobileByUid[uid] ?: (0L to 0L)
            val w = wifiByUid[uid] ?: (0L to 0L)
            if (m.first + m.second + w.first + w.second <= 0L) return@mapNotNull null
            val info = labels[uid]
            // 不要直接把 UID 当作应用名展示：无法映射的一律归为「未知应用」
            val displayName = info?.label
                ?: when (uid) {
                    1000 -> "Android 系统"
                    1001 -> "电话服务"
                    else -> "未知应用"
                }
            AppUsage(
                uid = uid,
                packageName = packages.firstOrNull { it.applicationInfo?.uid == uid }?.packageName
                    ?: "uid-$uid",
                label = displayName,
                icon = info?.icon,
                mobileRx = m.first,
                mobileTx = m.second,
                wifiRx = w.first,
                wifiTx = w.second,
                installed = info?.installed ?: 0L,
                updated = info?.updated ?: 0L,
                permissions = info?.permissions.orEmpty(),
                isSystem = info?.isSystem ?: (uid < 10000),
                isUnknown = info == null && uid != 1000 && uid != 1001
            )
        }.sortedByDescending { it.total }

        return apps to totalsFor(start, end)
    }

    /** 最近 days 天每天的移动/WiFi 流量，按时间从早到晚（过去 days-1 天 → 今天） */
    fun dailyTotals(days: Int): List<Pair<Totals, String>> {
        val fmt = SimpleDateFormat("E", Locale.CHINA)
        // fullDayRange 的正偏移表示未来，因此这里必须用负偏移取过去：-(days-1) → 0
        return (days - 1 downTo 0).map { daysAgo ->
            val offset = -daysAgo
            val (start, end) = fullDayRange(offset)
            val t = Totals.fromPair(
                querySummary(TYPE_MOBILE, start, end),
                querySummary(TYPE_WIFI, start, end)
            )
            t to if (daysAgo == 0) "今" else fmt.format(java.util.Date(start)).removePrefix("周")
        }
    }

    // 月历热力的缓存：同一个月份只查一次，翻月来回不重复扫
    private val monthCache = mutableMapOf<String, Map<Int, Totals>>()

    /**
     * 指定月份每天的总流量，用于日历上的强度标记。
     * 只统计到「今天」为止，未来日期不查（系统本来也没有记录）。
     * 返回 dayOfMonth -> Totals。
     */
    @Synchronized
    fun monthDailyTotals(year: Int, month: Int): Map<Int, Totals> {
        val key = "$year-${month + 1}"
        monthCache[key]?.let { return it }

        val first = Calendar.getInstance(Locale.CHINA).apply {
            set(year, month, 1, 0, 0, 0)
            clear(Calendar.MILLISECOND)
        }
        val lastDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance(Locale.CHINA)
        val isFutureMonth = year * 12 + month > today.get(Calendar.YEAR) * 12 + today.get(Calendar.MONTH)
        if (isFutureMonth) return emptyMap()

        val maxDay = if (year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH))
            today.get(Calendar.DAY_OF_MONTH) else lastDay

        val isCurrentMonth = year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH)
        val result = LinkedHashMap<Int, Totals>()
        for (day in 1..maxDay) {
            val start = Calendar.getInstance(Locale.CHINA).apply {
                set(year, month, day, 0, 0, 0)
                clear(Calendar.MILLISECOND)
            }.timeInMillis
            val end = Calendar.getInstance(Locale.CHINA).apply {
                set(year, month, day, 0, 0, 0)
                clear(Calendar.MILLISECOND)
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis.coerceAtMost(System.currentTimeMillis())
            if (end <= start) continue
            result[day] = totalsFor(start, end)
        }
        // 当月的数据每时每刻都在变，不缓存；历史月份才落缓存
        if (!isCurrentMonth) monthCache[key] = result
        return result
    }

    /** 今天 0 点到现在的每小时流量，24 个桶 */    fun hourlyTotals(): List<Pair<Long, Long>> {
        val result = MutableList(24) { 0L to 0L }
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            clear(Calendar.MILLISECOND)
        }
        val dayStart = cal.timeInMillis
        val now = System.currentTimeMillis()
        for (hour in 0..23) {
            val start = dayStart + hour * 3600L * 1000L
            val end = min(start + 3600L * 1000L, now)
            if (end <= start) break
            val mobile = querySummary(TYPE_MOBILE, start, end)
            val wifi = querySummary(TYPE_WIFI, start, end)
            result[hour] = (mobile.first + mobile.second) to (wifi.first + wifi.second)
        }
        return result
    }

    private data class PackageInfoLite(
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
        val installed: Long,
        val updated: Long,
        val permissions: List<String>,
        val isSystem: Boolean
    )

    private fun querySummary(type: Int, start: Long, end: Long): Pair<Long, Long> {
        if (end <= start) return Pair(0L, 0L)
        var stats: NetworkStats? = null
        var rx = 0L
        var tx = 0L
        try {
            stats = statsManager.querySummary(type, null, start, end) ?: return Pair(0L, 0L)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                rx += bucket.rxBytes
                tx += bucket.txBytes
            }
        } catch (e: Exception) {
            return Pair(0L, 0L)
        } finally {
            try {
                stats?.close()
            } catch (e: Exception) {
            }
        }
        return Pair(rx, tx)
    }

    private fun queryByUid(type: Int, start: Long, end: Long): Map<Int, Pair<Long, Long>> {
        val result = mutableMapOf<Int, Pair<Long, Long>>()
        var stats: NetworkStats? = null
        try {
            stats = statsManager.queryDetails(type, null, start, end)
            if (stats == null) return result
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                if (bucket.uid >= 0 && (bucket.rxBytes > 0 || bucket.txBytes > 0)) {
                    val previous = result[bucket.uid] ?: (0L to 0L)
                    result[bucket.uid] =
                        (previous.first + bucket.rxBytes) to (previous.second + bucket.txBytes)
                }
            }
        } catch (e: Exception) {
            // 无权限或机型不支持时返回空，UI 会给出提示
        } finally {
            try {
                stats?.close()
            } catch (e: Exception) {
            }
        }
        return result
    }

    private companion object {
        // ConnectivityManager.TYPE_MOBILE / TYPE_WIFI，NetworkStatsManager 使用这些整型值
        const val TYPE_MOBILE = 0
        const val TYPE_WIFI = 1
        const val DAY_MILLIS = 86_400_000L
    }
}
