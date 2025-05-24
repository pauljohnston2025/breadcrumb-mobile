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
    CARTO;

    fun attributionText(): List<String>
    {
        return when (this) {
            CUSTOM -> listOf("")
            ESRI -> listOf("Powered by Esri")
            GOOGLE -> listOf("©Google")
            OPENTOPOMAP -> listOf("©OpenTopoMap")
            OPENSTREETMAP -> listOf("©OpenStreetMap")
            STADIA -> listOf("©Stadia Maps", "©OpenMapTiles", "©OpenStreetMap")
            CARTO -> listOf("©CARTO")
            // else -> null // Or a default if you always want something
        }
    }

    fun attributionLink(): List<String>
    {
        return when (this) {
            CUSTOM -> listOf("")
            ESRI -> listOf("https://www.esri.com")
            GOOGLE -> listOf("https://cloud.google.com/maps-platform/terms")
            OPENTOPOMAP -> listOf("https://opentopomap.org/about")
            OPENSTREETMAP -> listOf("https://www.openstreetmap.org/copyright")
            STADIA -> listOf("https://stadiamaps.com/", "https://openmaptiles.org/", "https://www.openstreetmap.org/copyright")
            CARTO -> listOf("https://carto.com/attribution")
            // else -> null // Or a default if you always want something
        }
    }
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