package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice as CommonDevice
import com.garmin.android.connectiq.IQDevice

// do not make this a data class, if the internal state changes, we want o emit a new value
// data class only sees the device, and thinks we are the same object, but we are not
class CommonDeviceImpl(val device: IQDevice) :
    CommonDevice(device.friendlyName.toString(), device.status.toString(), device.deviceIdentifier) {
}