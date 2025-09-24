package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/getTilePalette")
class GetTilePalette

// todo cyle this every time the pallet changes
// we could make user configurable palettes?
// we could also make 'auto make palette from tiles'
// want to add a 'mips palette option'
val PALETTE_ID = 1;

@Serializable
data class GetTilePaletteResponse(
    val id: Int,
    val data: List<Int>
)
// without a json response garmin fails with '-400 INVALID_HTTP_BODY_IN_NETWORK_RESPONSE'