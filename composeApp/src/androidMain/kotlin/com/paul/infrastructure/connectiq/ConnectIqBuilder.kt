package com.paul.infrastructure.connectiq

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.IQConnectType
import com.paul.infrastructure.service.isEmulator

class ConnectIqBuilder(private val context: Context) {

    fun getInstance(): ConnectIQ {
        if (isEmulator()) {
            // TETHERED for emulator
            return ConnectIQ.getInstance(context, IQConnectType.TETHERED)
        }

        return ConnectIQ.getInstance(context, IQConnectType.WIRELESS)
    }
}