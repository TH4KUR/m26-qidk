package com.megathon.incident.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Process
import android.provider.Settings

/** What the proof sheet may truthfully say about the network. Measured, never assumed. */
data class NetworkProof(val airplaneMode: Boolean, val networkAvailable: Boolean, val bytesSent: Long?) {
    /** Short line for the main screen. */
    val headline: String
        get() = when {
            airplaneMode -> "Airplane mode on"
            networkAvailable -> "Network on, this app sends nothing"
            else -> "No network"
        }
}

class NetworkMeter(private val context: Context) {
    private val uid = Process.myUid()
    private val baseline = TrafficStats.getUidTxBytes(uid)

    fun read(): NetworkProof {
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val available = cm?.activeNetwork != null
        val now = TrafficStats.getUidTxBytes(uid)
        val sent = if (baseline == TrafficStats.UNSUPPORTED.toLong() || now == TrafficStats.UNSUPPORTED.toLong()) null else (now - baseline).coerceAtLeast(0)
        return NetworkProof(airplane, available, sent)
    }
}
