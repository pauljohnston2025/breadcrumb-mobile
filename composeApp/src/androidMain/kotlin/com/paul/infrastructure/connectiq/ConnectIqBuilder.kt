package com.paul.infrastructure.connectiq

import android.content.Context
import android.os.Build
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.IQConnectType

class ConnectIqBuilder(private val context: Context) {

    fun getInstance(): ConnectIQ {
        // google/sdk_gphone_x86_64/emu64xa:13/TE1A.220922.025/9795748:userdebug/dev-keys
        if (Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emu")) {
            // TETHERED for emulator
            return ConnectIQ.getInstance(context, IQConnectType.TETHERED)
        }

        return ConnectIQ.getInstance(context, IQConnectType.WIRELESS)
    }
}