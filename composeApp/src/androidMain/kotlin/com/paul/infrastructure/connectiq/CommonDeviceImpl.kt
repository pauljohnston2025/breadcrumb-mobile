package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice as CommonDevice
import com.garmin.android.connectiq.IQDevice

class CommonDeviceImpl(val device: IQDevice) :
    CommonDevice(device.friendlyName.toString(), device.status.toString()) {
}