package com.paul.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ServerType {
    CUSTOM,
    ESRI,
    GOOGLE,
    OPENTOPOMAP,
    OPENSTREETMAP,
}

@Serializable
data class TileServerInfo(
    val serverType: ServerType,
    val title: String,
    val url: String,
    val tileLayerMin: Int,
    val tileLayerMax: Int,
    val isCustom: Boolean = false // Flag to identify user-added servers
)