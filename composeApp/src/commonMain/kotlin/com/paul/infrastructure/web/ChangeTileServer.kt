package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/changeTileServer")
data class ChangeTileServer(
    val tileServer: String,
)
