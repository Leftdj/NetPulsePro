package com.flowstats.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Wi-Fi 连接的详细信息。
 *
 * 设计原则：拿不到的字段一律为 null，由 UI 显示「设备不支持」，
 * 绝不用推算值填充，避免把估算当成实测。
 */
data class WifiDetail(
    val isWifi: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val frequencyMhz: Int? = null,
    val band: String? = null,
    val standard: String? = null,
    /** 系统上报的协商速率（PHY），不是真实吞吐 */
    val linkSpeedMbps: Int? = null,
    val rxLinkMbps: Int? = null,
    val txLinkMbps: Int? = null,
    val maxRxMbps: Int? = null,
    val channel: Int? = null,
    /** 只有扫描缓存里能匹配到当前 BSSID 时才有值 */
    val channelWidthMhz: Int? = null,
    val rssi: Int? = null,
    val signalLevel: Int? = null,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val dns: List<String> = emptyList(),
    val mtu: Int? = null,
    val gateway: String? = null,
    val privateDns: String? = null,
    /** 物理接口名，如 wlan0 / rmnet_data0 */
    val interfaceName: String? = null
) {
    val hasAnyWifi: Boolean get() = isWifi
}

object WifiInfoProvider {

    fun read(context: Context): WifiDetail {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return WifiDetail()
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val lp = network?.let { cm.getLinkProperties(it) }
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val base = WifiDetail(
            isWifi = isWifi,
            ipv4 = lp?.linkAddresses?.firstOrNull {
                it.address is java.net.Inet4Address
            }?.address?.hostAddress,
            ipv6 = lp?.linkAddresses?.firstOrNull {
                val a = it.address
                a is java.net.Inet6Address && !a.isLinkLocalAddress && !a.isLoopbackAddress
            }?.address?.hostAddress,
            dns = lp?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList(),
            mtu = readMtu(lp),
            gateway = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress,
            privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                lp?.privateDnsServerName else null,
            interfaceName = lp?.interfaceName
        )

        if (!isWifi) return base

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return base
        @Suppress("DEPRECATION")
        val info: WifiInfo = wm.connectionInfo ?: return base

        // Android 10 起 SSID / BSSID 需要定位权限，否则系统返回占位符
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val rawSsid = info.ssid
        val ssid = when {
            rawSsid == null -> null
            rawSsid == WifiManager.UNKNOWN_SSID -> null
            rawSsid == "<unknown ssid>" -> null
            rawSsid.startsWith("\"") && rawSsid.endsWith("\"") -> rawSsid.trim('"')
            else -> rawSsid
        }?.takeIf { hasLocation || it.isNotBlank() }

        val bssid = info.bssid?.let { if (it == "02:00:00:00:00:00") null else it }

        val freq = info.frequency.takeIf { it > 0 }
        val band = freq?.let { f ->
            when {
                f in 2400..2500 -> "2.4 GHz"
                f in 4900..5925 -> "5 GHz"
                f in 5926..7125 -> "6 GHz"
                f in 57000..71000 -> "60 GHz"
                else -> null
            }
        }

        val standard = readStandard(info, band)

        val rx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            info.rxLinkSpeedMbps.takeIf { it > 0 } else null
        val tx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            info.txLinkSpeedMbps.takeIf { it > 0 } else null
        @Suppress("DEPRECATION")
        val legacy = info.linkSpeed.takeIf { it > 0 }
        val maxRx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            info.maxSupportedRxLinkSpeedMbps.takeIf { it > 0 } else null

        val rssi = info.rssi.takeIf { it < 0 && it > -127 }

        val channelWidth = if (hasLocation) readChannelWidth(wm, bssid) else null

        return base.copy(
            ssid = ssid,
            bssid = bssid,
            frequencyMhz = freq,
            band = band,
            standard = standard,
            linkSpeedMbps = legacy ?: rx,
            rxLinkMbps = rx,
            txLinkMbps = tx,
            maxRxMbps = maxRx,
            channel = freq?.let { channelOf(it) },
            channelWidthMhz = channelWidth,
            rssi = rssi,
            signalLevel = rssi?.let { levelOf(it) }
        )
    }

    /**
     * MTU 优先读 LinkProperties，但部分机型（尤其定制 ROM）这里会返回 0，
     * 此时退化到 NetworkInterface 读取内核给该接口下发的 MTU。
     * 两处都拿不到才返回 null，UI 显示「系统未上报」。
     */
    private fun readMtu(lp: LinkProperties?): Int? {
        lp?.mtu?.takeIf { it > 0 }?.let { return it }
        val name = lp?.interfaceName ?: return null
        return runCatching {
            java.net.NetworkInterface.getByName(name)?.mtu?.takeIf { it > 0 }
        }.getOrNull()
    }

    private fun readStandard(info: WifiInfo, band: String?): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return when (info.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 3 (802.11g)"
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
            ScanResult.WIFI_STANDARD_11AX ->
                if (band == "6 GHz") "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
            ScanResult.WIFI_STANDARD_11AD -> "WiGig (802.11ad)"
            8 -> "Wi-Fi 7 (802.11be)"
            else -> null
        }
    }

    /** 中心频率换算信道号 */
    private fun channelOf(freqMhz: Int): Int? = when (freqMhz) {
        2484 -> 14
        in 2412..2472 -> (freqMhz - 2407) / 5
        in 4910..4980 -> (freqMhz - 4000) / 5
        in 5000..5895 -> (freqMhz - 5000) / 5
        in 5955..7115 -> (freqMhz - 5950) / 5
        else -> null
    }

    private fun levelOf(rssi: Int): Int = when {
        rssi >= -50 -> 4
        rssi >= -60 -> 3
        rssi >= -70 -> 2
        rssi >= -80 -> 1
        else -> 0
    }

    /**
     * 频宽没有直接 API，只能从扫描缓存里按 BSSID 匹配当前 AP。
     * 系统对扫描有频率限制（约 4 次 / 2 分钟），所以这是「最近一次扫描值」，不是实时值。
     */
    private fun readChannelWidth(wm: WifiManager, bssid: String?): Int? {
        if (bssid == null) return null
        val results = runCatching { wm.scanResults }.getOrNull() ?: return null
        val match = results.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) } ?: return null
        return when (match.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> 20
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ -> 160
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
            5 -> 320
            else -> null
        }
    }

    /** 蜂窝网络制式，主要用于总览页显示当前接入类型 */
    fun cellularLabel(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return "Wi-Fi"
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return "移动网络"
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java) ?: return "移动网络"
        @Suppress("DEPRECATION")
        val type = runCatching { tm.dataNetworkType }.getOrNull() ?: return "移动网络"
        return when (type) {
            android.telephony.TelephonyManager.NETWORK_TYPE_NR -> "5G"
            android.telephony.TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA,
            android.telephony.TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            android.telephony.TelephonyManager.NETWORK_TYPE_EDGE,
            android.telephony.TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "移动网络"
        }
    }
}
