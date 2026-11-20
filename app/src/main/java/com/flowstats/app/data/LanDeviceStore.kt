package com.flowstats.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 局域网设备的用户自定义信息：备注名、收藏、忽略。
 * 只保存用户主动设置过的内容，绝不预填或伪造设备信息。
 * 按 IP 作为键（同一网段内稳定），拿不到设备名时不影响备注功能。
 */
data class DeviceMeta(
    val note: String = "",
    val favorite: Boolean = false,
    val ignored: Boolean = false
)

object LanDeviceStore {
    private const val PREF = "lan_device_meta"
    private const val KEY = "meta"

    fun load(context: Context): Map<String, DeviceMeta> {
        val raw = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = mutableMapOf<String, DeviceMeta>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                map[o.optString("ip")] = DeviceMeta(
                    note = o.optString("n", ""),
                    favorite = o.optBoolean("f", false),
                    ignored = o.optBoolean("i", false)
                )
            }
            map
        }.getOrElse { emptyMap() }
    }

    fun save(context: Context, meta: Map<String, DeviceMeta>) {
        val arr = JSONArray()
        meta.forEach { (ip, m) ->
            val o = JSONObject()
            o.put("ip", ip); o.put("n", m.note)
            o.put("f", m.favorite); o.put("i", m.ignored)
            arr.put(o)
        }
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun update(context: Context, ip: String, transform: (DeviceMeta) -> DeviceMeta): Map<String, DeviceMeta> {
        val map = load(context).toMutableMap()
        map[ip] = transform(map[ip] ?: DeviceMeta())
        save(context, map)
        return map
    }
}
