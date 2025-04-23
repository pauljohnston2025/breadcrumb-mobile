package com.paul.infrastructure.web

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
