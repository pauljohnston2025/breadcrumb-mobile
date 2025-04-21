package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

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
data class LoadTileResponse(val data: String)