package com.flowstats.app.data

import kotlin.math.roundToInt

/**
 * 智能诊断引擎：把已采集的真实指标（ICMP / DNS / Wi-Fi 链路）按规则归纳成
 * 结论 + 分级 + 建议。所有数值都来自实测，缺数据一律标注「未测量」，不做任何估算或伪造。
 */
object DiagnosisEngine {

    enum class Level(val label: String) {
        EXCELLENT("优秀"), GOOD("良好"), FAIR("一般"), POOR("较差"), BAD("很差"), UNKNOWN("未测量")
    }

    data class Metric(
        val label: String,
        val value: String,
        /** 0..1，仅用于条形长度的相对刻度；无可测数据时为 null */
        val ratio: Float?,
        val level: Level
    )

    data class Suggestion(
        val warning: Boolean,
        val text: String
    )

    data class Report(
        val overall: Level,
        val headline: String,
        val metrics: List<Metric>,
        val suggestions: List<Suggestion>
    )

    /**
     * @param ping ICMP 实测；@param wifi Wi-Fi 链路；@param dnsProbes DNS 对比；@param hasIpv6 是否有公网 IPv6
     */
    fun analyze(
        ping: PingReport,
        wifi: WifiDetail,
        dnsProbes: List<DnsProbe>,
        hasIpv6: Boolean,
        isMobile: Boolean
    ): Report {
        val metrics = mutableListOf<Metric>()
        val suggestions = mutableListOf<Suggestion>()
        var score = 0
        var counted = 0

        // ── 延迟 ────────────────────────────────
        if (ping.ok) {
            val rtt = ping.rttAvg
            val (lv, ratio) = when {
                rtt < 30 -> Level.EXCELLENT to (rtt / 30.0)
                rtt < 80 -> Level.GOOD to (0.33 + (rtt - 30) / 50.0 * 0.22)
                rtt < 150 -> Level.FAIR to (0.55 + (rtt - 80) / 70.0 * 0.2)
                rtt < 300 -> Level.POOR to (0.75 + (rtt - 150) / 150.0 * 0.2)
                else -> Level.BAD to 1.0
            }
            metrics += Metric("平均延迟", "${rtt.roundToInt()} ms", ratio.coerceIn(0.0, 1.0).toFloat(), lv)
            score += lv.points(); counted++
            if (rtt >= 80) suggestions += Suggestion(true, "平均延迟 ${rtt.roundToInt()} ms 偏高，可能存在网络拥塞或目标较远，建议错峰或靠近路由器复测。")
        } else {
            metrics += Metric("平均延迟", "未测量", null, Level.UNKNOWN)
        }

        // ── 抖动 ────────────────────────────────
        if (ping.ok && ping.samples.size >= 2) {
            val j = ping.jitter
            val (lv, ratio) = when {
                j < 10 -> Level.EXCELLENT to (j / 10.0)
                j < 30 -> Level.GOOD to (0.33 + (j - 10) / 20.0 * 0.22)
                j < 50 -> Level.FAIR to (0.55 + (j - 30) / 20.0 * 0.2)
                else -> Level.POOR to 1.0
            }
            metrics += Metric("网络抖动", "${j.roundToInt()} ms", ratio.coerceIn(0.0, 1.0).toFloat(), lv)
            score += lv.points(); counted++
            if (j >= 30) suggestions += Suggestion(true, "抖动 ${j.roundToInt()} ms 较大，实时业务（语音/游戏）可能卡顿，建议排查无线干扰或带宽占用。")
        } else {
            metrics += Metric("网络抖动", "未测量", null, Level.UNKNOWN)
        }

        // ── 丢包 ────────────────────────────────
        if (ping.ok) {
            val loss = ping.lossPercent
            val (lv, ratio) = when {
                loss == 0 -> Level.EXCELLENT to 0f
                loss < 1 -> Level.GOOD to 0.3f
                loss < 5 -> Level.FAIR to 0.55f
                loss < 15 -> Level.POOR to 0.8f
                else -> Level.BAD to 1f
            }
            metrics += Metric("丢包率", "$loss%", ratio, lv)
            score += lv.points(); counted++
            if (loss >= 5) suggestions += Suggestion(true, "丢包率 $loss% 偏高，连接稳定性差，建议检查 Wi-Fi 信号、线缆或联系运营商。")
        } else {
            metrics += Metric("丢包率", "未测量", null, Level.UNKNOWN)
        }

        // ── 信号强度（仅 Wi-Fi）──────────────────
        if (wifi.isWifi && wifi.rssi != null) {
            val r = wifi.rssi
            val (lv, ratio) = when {
                r >= -55 -> Level.EXCELLENT to 0.1f
                r >= -70 -> Level.GOOD to 0.4f
                r >= -80 -> Level.FAIR to 0.7f
                else -> Level.POOR to 1f
            }
            // 信号越强，条越“好”：这里用 (1 - ratio) 的反向不好理解，统一用“问题程度”比例，越大越差
            metrics += Metric("信号强度", "$r dBm", ratio, lv)
            score += lv.points(); counted++
            if (r < -70) suggestions += Suggestion(true, "Wi-Fi 信号 $r dBm 偏弱，建议靠近路由器或减少墙体遮挡。")
        }

        // ── 协商速率（仅 Wi-Fi，注明非实测吞吐）──
        if (wifi.isWifi) {
            val link = wifi.rxLinkMbps ?: wifi.linkSpeedMbps
            if (link != null && link > 0) {
                val lv = when {
                    link >= 1200 -> Level.EXCELLENT
                    link >= 600 -> Level.GOOD
                    link >= 300 -> Level.FAIR
                    else -> Level.POOR
                }
                metrics += Metric("协商速率", "$link Mbps", null, lv)
            } else {
                metrics += Metric("协商速率", "设备不支持", null, Level.UNKNOWN)
            }
        }

        // ── DNS 解析对比 ────────────────────────
        val okProbes = dnsProbes.mapNotNull { it.ms?.let { ms -> it.label to ms } }
        if (okProbes.isNotEmpty()) {
            val fastest = okProbes.minByOrNull { it.second }
            val slowest = okProbes.maxByOrNull { it.second }
            val avg = okProbes.map { it.second }.average()
            val lv = when {
                avg < 20 -> Level.EXCELLENT
                avg < 60 -> Level.GOOD
                avg < 120 -> Level.FAIR
                else -> Level.POOR
            }
            metrics += Metric("DNS 解析", "${avg.roundToInt()} ms", null, lv)
            score += lv.points(); counted++
            if (fastest != null && slowest != null && slowest.second > fastest.second * 3 && slowest.second - fastest.second > 40) {
                suggestions += Suggestion(false, "DNS 服务器差异明显：${fastest.first} 约 ${fastest.second.roundToInt()} ms，${slowest.first} 约 ${slowest.second.roundToInt()} ms，可优先使用较快的 DNS。")
            }
        } else {
            metrics += Metric("DNS 解析", "未测量", null, Level.UNKNOWN)
        }

        // ── IPv6 连通 ───────────────────────────
        if (!isMobile) {
            metrics += Metric("公网 IPv6", if (hasIpv6) "已连通" else "未连通", null,
                if (hasIpv6) Level.GOOD else Level.FAIR)
            if (!hasIpv6) suggestions += Suggestion(false, "当前未检测到公网 IPv6，如需访问 IPv6 资源请确认光猫/路由器已开启 IPv6。")
        }

        // ── 网络可用（测量失败）──────────────────
        if (!ping.ok && ping.error != null) {
            suggestions += Suggestion(true, "ICMP 测量失败：${ping.error}，当前无法判断链路质量，请检查网络后重试。")
        }

        if (suggestions.isEmpty()) {
            suggestions += Suggestion(false, "各项指标正常，当前链路状态良好，可以继续正常使用。")
        }

        val overall = if (counted == 0) Level.UNKNOWN else when {
            score >= counted * 4 -> Level.EXCELLENT
            score >= counted * 3 -> Level.GOOD
            score >= counted * 2 -> Level.FAIR
            else -> Level.POOR
        }
        val headline = when (overall) {
            Level.EXCELLENT -> "链路状态优秀，各项指标表现良好"
            Level.GOOD -> "链路状态良好，可满足日常使用"
            Level.FAIR -> "链路存在波动，部分指标需要关注"
            Level.POOR -> "链路质量较差，建议按下方建议排查"
            Level.UNKNOWN -> "数据不足，进入网络页会自动采集真实指标"
            else -> "链路状态一般"
        }
        return Report(overall, headline, metrics, suggestions)
    }

    private fun Level.points() = when (this) {
        Level.EXCELLENT -> 4
        Level.GOOD -> 3
        Level.FAIR -> 2
        Level.POOR -> 1
        Level.BAD -> 0
        Level.UNKNOWN -> 0
    }
}
