package com.paul.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ServerType {
    CUSTOM,
    ESRI,
    GOOGLE,
    OPENTOPOMAP,
    OPENSTREETMAP,
    STADIA,
    CARTO,
}

@Serializable
data class TileServerInfo(
    val serverType: ServerType,
    val title: String,
    val url: String,
    val tileLayerMin: Int,
    val tileLayerMax: Int,
    val isCustom: Boolean = false, // Flag to identify user-added servers
    val id: String = title, // should probably be a uuid, but we want the file system to be readable for now (if the name changes on custom servers then we will have to re-download tiles, not great but should be fairly rare)
)