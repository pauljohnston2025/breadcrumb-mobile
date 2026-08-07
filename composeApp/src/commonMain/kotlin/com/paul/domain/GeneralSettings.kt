package com.paul.domain

import kotlinx.serialization.Serializable

@Serializable
data class GeneralSettings(
    val fitMimeGroupEnabled: Boolean = false,
    val chartYearRange: Int = 10,
) {
    companion object {
        val default = GeneralSettings()
    }
}
