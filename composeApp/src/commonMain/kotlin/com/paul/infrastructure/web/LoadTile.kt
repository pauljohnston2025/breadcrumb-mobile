package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
enum class TileType(val value: UByte) {
    TILE_DATA_TYPE_64_COLOUR(0u),
    TILE_DATA_TYPE_BASE64_FULL_COLOUR(1u),
    TILE_DATA_TYPE_BLACK_AND_WHITE(2u);

    fun label(): String {
        return when (this) {
            TILE_DATA_TYPE_BLACK_AND_WHITE -> "Black and White (fastest)"
            TILE_DATA_TYPE_64_COLOUR -> "64 Colours (balanced)"
            TILE_DATA_TYPE_BASE64_FULL_COLOUR -> "Full Colour (slow)"
        }
    }
}

@Serializable
@Resource("/loadtile")
data class LoadTileRequest(
    val x: Int,
    val y: Int,
    val z: Int,
    val tileSize: Int,
    val scaledTileSize: Int,
)

@Serializable
data class LoadTileResponse(
    val type: Int,
    val data: String
)

@Serializable
class ErrorJson
// without a json response garmin fails with '-400 INVALID_HTTP_BODY_IN_NETWORK_RESPONSE'