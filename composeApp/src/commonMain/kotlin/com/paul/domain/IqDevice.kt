package com.paul.domain

open class IqDevice(
    var friendlyName: String,
    val status: String,
    val id: Long,
) {
    override fun toString(): String {
        return "$friendlyName $status "
    }
}