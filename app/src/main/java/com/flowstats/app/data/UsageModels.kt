package com.flowstats.app.data

import android.graphics.drawable.Drawable

enum class Period(val label: String) {
    DAY("日"),
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

data class Totals(
    val mobileRx: Long = 0L,
    val mobileTx: Long = 0L,
    val wifiRx: Long = 0L,
    val wifiTx: Long = 0L
) {
    val mobile: Long get() = mobileRx + mobileTx
    val wifi: Long get() = wifiRx + wifiTx
    val total: Long get() = mobile + wifi

    /** 全通道下行 / 上行汇总 */
    val rx: Long get() = mobileRx + wifiRx
    val tx: Long get() = mobileTx + wifiTx

    operator fun plus(other: Totals) = Totals(
        mobileRx + other.mobileRx,
        mobileTx + other.mobileTx,
        wifiRx + other.wifiRx,
        wifiTx + other.wifiTx
    )

    companion object {
        fun fromPair(mobile: Pair<Long, Long>, wifi: Pair<Long, Long>) = Totals(
            mobileRx = mobile.first,
            mobileTx = mobile.second,
            wifiRx = wifi.first,
            wifiTx = wifi.second
        )
    }
}

data class AppUsage(
    val uid: Int,
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val mobileRx: Long,
    val mobileTx: Long,
    val wifiRx: Long,
    val wifiTx: Long,
    val installed: Long = 0L,
    val updated: Long = 0L,
    val permissions: List<String> = emptyList(),
    val isSystem: Boolean = false,
    /** 无法映射到已安装应用（已卸载残留 / 独立后台进程），列表里不直接暴露 UID */
    val isUnknown: Boolean = false
) {
    val mobile: Long get() = mobileRx + mobileTx
    val wifi: Long get() = wifiRx + wifiTx
    val total: Long get() = mobile + wifi
}

data class SpeedSample(
    val download: Float = 0f,
    val upload: Float = 0f,
    val time: Long = 0L
)
