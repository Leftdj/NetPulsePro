package com.flowstats.app.data

import android.content.Context
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.max

object SpeedMonitor {

    /** 每秒采样一次系统累计收发字节数，差值为即时上下行速率（bytes/s） */
    fun flow(context: Context, intervalMs: Long = 1000L): Flow<SpeedSample> = callbackFlow {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastTime = SystemClock.elapsedRealtime()
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                val rx = TrafficStats.getTotalRxBytes()
                val tx = TrafficStats.getTotalTxBytes()
                val now = SystemClock.elapsedRealtime()
                val seconds = max((now - lastTime) / 1000f, 0.001f)
                val download = max(rx - lastRx, 0L) / seconds
                val upload = max(tx - lastTx, 0L) / seconds
                trySend(SpeedSample(download, upload, System.currentTimeMillis()))
                lastRx = rx
                lastTx = tx
                lastTime = now
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(runnable, intervalMs)
        awaitClose { handler.removeCallbacks(runnable) }
    }
}
