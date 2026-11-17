package com.flowstats.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 更新日志条目：版本、build 号、改动说明 */
data class ReleaseNote(
    val version: String,
    val build: Int,
    val notes: String
)

/** 检测更新结果 */
sealed interface UpdateResult {
    data class Available(val version: String, val url: String, val notes: String) : UpdateResult
    data object NoReleases : UpdateResult
    data class Error(val message: String) : UpdateResult
}

object AppUpdate {
    const val GITHUB_URL = "https://github.com/zJ681/NetPulsePro"
    private const val API = "https://api.github.com/repos/zJ681/NetPulsePro/releases/latest"

    /**
     * 本项目的真实迭代记录（与 build.gradle.kts 的 versionCode / versionName 对应）。
     * 不填充截图里其它 App 的版本号，避免把别人的数据当成自己的展示。
     */
    val history: List<ReleaseNote> = listOf(
        ReleaseNote("v3.3", 12, "新增版本信息与更新检测；网络参数每 2 秒自动刷新；应用详情直达系统信息页"),
        ReleaseNote("v3.2", 11, "应用详情三点直达系统信息、网络参数 2 秒自动刷新、抽屉与菜单动画"),
        ReleaseNote("v3.1", 10, "日历选日期与热力图、延迟/抖动/丢包自动刷新、套餐自定义、网络参数补全"),
        ReleaseNote("v3.0", 9, "新增总览与网络诊断页、Wi-Fi 链路详情、网络事件时间轴"),
        ReleaseNote("v2.5", 8, "动画开关、深色模式、套餐预警、圆环标签与图表提示优化"),
        ReleaseNote("v2.0", 5, "界面重做：蓝紫渐变、玻璃卡片、四页导航与实时曲线动画")
    )

    /** 读取 GitHub Releases；仓库暂无发布时返回 NoReleases，网络异常返回 Error */
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NetPulsePro")
            }
            when (val code = conn.responseCode) {
                200 -> {
                    val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val tag = obj.optString("tag_name", "")
                    val name = obj.optString("name", tag).ifBlank { tag }
                    UpdateResult.Available(
                        version = name,
                        url = obj.optString("html_url", GITHUB_URL),
                        notes = obj.optString("body", "")
                    )
                }
                404 -> UpdateResult.NoReleases
                else -> UpdateResult.Error("服务器返回 $code")
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "网络请求失败")
        } finally {
            conn?.disconnect()
        }
    }
}
