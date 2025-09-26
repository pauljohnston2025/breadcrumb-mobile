package com.paul.infrastructure.web

import com.paul.domain.ColourPalette
import com.paul.domain.TileServerInfo
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/changeTileServer")
data class ChangeTileServer(
    val tileServer: TileServerInfo,
)

@Serializable
@Resource("/changeAuthToken")
data class ChangeAuthToken(
    val authToken: String,
)

@Serializable
@Resource("/changeTileType")
data class ChangeTileType(
    val tileType: TileType,
)

// cannot use resources plugin, as the nested colour params are serialised incorrectly
//@Serializable
//@Resource("/changeColourPalette")
//data class ChangeColourPalette(
//    val colourPalette: ColourPalette
//)
