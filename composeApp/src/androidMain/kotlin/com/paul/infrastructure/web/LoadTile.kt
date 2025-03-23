package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/loadtile")
data class LoadTileRequest(
    val lat: Float,
    val long: Float,
    val tileX: Int,
    val tileY: Int,
    val scale: Float,
    val tileSize: Int,
    val tileCountXY: Int,
)

@Serializable
data class LoadTileResponse(val data: List<Int>)