package com.paul.domain

import kotlinx.serialization.Serializable

@Serializable
data class RGBColor(val r: Int, val g: Int, val b: Int) {
    fun toMonkeyCColourInt(): Int {
        // The 0xFF (255) represents a fully opaque alpha channel.
        // 'shl' is the bitwise shift left operator in Kotlin.
        // 'or' is the bitwise OR operator in Kotlin.
        return (0xFF shl 24) or (r.toUByte().toInt() shl 16) or (g.toUByte().toInt() shl 8) or b.toUByte().toInt()
// no leading FF, monkeyc will take the 3 byte colour as a number
//        return (r.toUByte().toInt() shl 16) or (g.toUByte().toInt() shl 8) or b.toUByte().toInt()
    }
}