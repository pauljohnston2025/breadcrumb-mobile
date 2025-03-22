package com.paul.infrastructure.utils

import android.os.Build

fun isEmulator(): Boolean {
    // google/sdk_gphone_x86_64/emu64xa:13/TE1A.220922.025/9795748:userdebug/dev-keys
    return Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emu");
}