package com.paul.domain

import kotlinx.serialization.Serializable

@Serializable
data class GeneralSettings(
    val fitMimeGroupEnabled: Boolean = false,
) {
    companion object {
        val default = GeneralSettings()
    }
}
