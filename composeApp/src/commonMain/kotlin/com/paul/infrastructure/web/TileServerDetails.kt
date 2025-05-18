package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/tileServerDetails")
class TileServerDetails

@Serializable
data class TileServerDetailsResponse(
    val tileLayerMin: Int,
    val tileLayerMax: Int
)