package com.paul.domain

open class IqDevice(
    val friendlyName: String,
    val status: String
) {
    override fun toString(): String {
        return "$friendlyName $status "
    }
}