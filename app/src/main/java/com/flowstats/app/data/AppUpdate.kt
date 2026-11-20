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
    /** 发现新版本；url 为 APK 直链，pageUrl 为下载页 */
    data class Available(
        val version: String,
        val code: Int,
        val url: String,
        val pageUrl: String,
        val notes: String
    ) : UpdateResult

    /** 已是最新版本 */
    data object UpToDate : UpdateResult

    /** 版本清单不存在（下载站未配置） */
    data object NoReleases : UpdateResult

    data class Error(val message: String) : UpdateResult
}

object AppUpdate {
    const val GITHUB_URL = "https://github.com/Leftdj/NetPulsePro"

    /**
     * 版本清单位于我们自己的下载站，随每次发布一起更新，因此检测结果真实可靠。
     * 此前读 GitHub Releases 的接口在仓库无 Release 时恒返回 404，导致"更新不了"。
     * 域名与分享码和二维码一致；分享码固定，未随重新部署变化。
     */
    private const val VERSION_URL =
        "https://wbapp-pcvohude0fh0wwjb1nqfag.app.workbuddy.host/version.json?sharecode=TZJDFTD3SN"

    /** 本项目真实迭代记录，与 build.gradle.kts 的 versionCode / versionName 对应 */
    val history: List<ReleaseNote> = listOf(
        ReleaseNote("v5.3", 32, "局域网设备图标改为矢量线性风格（路由器/显示器/手机）"),
        ReleaseNote("v5.2", 31, "修复局域网扫描结果卡片文字重叠"),
        ReleaseNote("v5.1", 30, "局域网模块第二轮：统计去重、扫描状态、设备详情页、拓扑交互与列表增强"),
        ReleaseNote("v5.0", 29, "启动流畅度优化：历史数据加载异步化、设备轮询降至 2 秒、减少首屏重组"),
        ReleaseNote("v4.9", 28, "DNS 自动刷新改为 1 秒并删除设置项；修复年→月/周/日切换回到年初"),
        ReleaseNote("v4.8", 27, "修正 AP 排序/相对拥挤指数/网关拆分/设备发现标注；新增 DFS 提醒、扫描状态、RSSI 最强值与采样时间、扫描进度与停止、DNS 间隔"),
        ReleaseNote("v4.7", 26, "新增局域网设备扫描、事件中心、一键体检与多目标监控；修复年模式列表为空"),
        ReleaseNote("v4.6", 25, "新增 Wi-Fi 信道分析仪与信号测试记录（RSSI 曲线、位置快照）"),
        ReleaseNote("v4.5", 24, "修复年→日切换落到年初导致列表空白；排行 1-5 名各用不同配色；底部导航换矢量图标"),
        ReleaseNote("v4.4", 23, "修复日期弹窗被底部导航栏遮挡：弹窗改到根层级绘制，并限制最大高度、内容可滚动"),
        ReleaseNote("v4.3", 22, "月/年模式的日期选择改为网格：月模式直接点月份、年模式直接点年份，不用逐月翻"),
        ReleaseNote("v4.2", 21, "链路质量历史按频段/频宽评级：2.4G 与 5G 采用各自参考上限，修复 2.4G 344 Mbps 被判为很差"),
        ReleaseNote("v4.1", 20, "应用图标更换为蓝紫渐变信号波形样式"),
        ReleaseNote("v4.0", 19, "新增检测历史：今天/昨天/本周统计、按天归档、自动快照与 CSV 导出"),
        ReleaseNote("v3.9", 18, "运行日志升级：本地持久化、分类标签、条数统计、导出日志 CSV 与全屏查看页"),
        ReleaseNote("v3.8", 17, "新增智能诊断：基于真实测量生成综合评估、指标条形图与排查建议"),
        ReleaseNote("v3.7", 16, "新增 CSV 报表导出：日流量、应用排行、时段分布一键导出到下载目录"),
        ReleaseNote("v3.6", 15, "修复检测更新：改读下载站版本清单、支持一键下载；补齐更新日志"),
        ReleaseNote("v3.5", 14, "新增链路质量历史与颜色分级，协商速率/RSSI/延迟均为真实采样"),
        ReleaseNote("v3.4", 13, "修正开源仓库地址、修复抽屉遮罩不显示的 bug"),
        ReleaseNote("v3.3", 12, "新增版本信息弹窗、更新日志、检测更新与 GitHub 入口"),
        ReleaseNote("v3.2", 11, "应用详情三点直达系统信息、网络参数 2 秒自动刷新、抽屉与菜单动画"),
        ReleaseNote("v3.1", 10, "日历选日期与热力图、延迟/抖动/丢包自动刷新、套餐自定义、网络参数补全"),
        ReleaseNote("v3.0", 9, "新增总览与网络诊断页、Wi-Fi 链路详情、网络事件时间轴"),
        ReleaseNote("v2.5", 8, "动画开关、深色模式、套餐预警、圆环标签与图表提示优化"),
        ReleaseNote("v2.0", 5, "界面重做：蓝紫渐变、玻璃卡片、四页导航与实时曲线动画")
    )

    /**
     * 读取下载站版本清单并与本地 versionCode 比较。
     * - 远端 code > 本地 → Available
     * - 远端 code <= 本地 → UpToDate
     */
    suspend fun check(currentCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NetPulsePro")
            }
            when (val code = conn.responseCode) {
                200 -> {
                    val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val remoteCode = obj.optInt("versionCode", 0)
                    val version = obj.optString("versionName", "").ifBlank { "未知" }
                    if (remoteCode > currentCode) {
                        UpdateResult.Available(
                            version = version,
                            code = remoteCode,
                            url = obj.optString("apkUrl", GITHUB_URL),
                            pageUrl = obj.optString("pageUrl", GITHUB_URL),
                            notes = obj.optString("notes", "")
                        )
                    } else {
                        UpdateResult.UpToDate
                    }
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
