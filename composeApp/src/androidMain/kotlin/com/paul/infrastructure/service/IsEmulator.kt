package com.paul.infrastructure.service

import android.os.Build

fun isEmulator(): Boolean {
    // use when we want to test with physical device connected to the garmin simulator
    return true

    // google/sdk_gphone_x86_64/emu64xa:13/TE1A.220922.025/9795748:userdebug/dev-keys
    return Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emu");
}