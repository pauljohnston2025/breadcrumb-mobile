package com.paul.domain

import com.paul.infrastructure.web.GetTilePaletteResponse
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
enum class PaletteMappingMode {
    NEAREST_NEIGHBOR,
    ORDERED_BY_BRIGHTNESS,
    CIELAB,
    PALETTE_REMAP
}

@Serializable
data class ColourPalette(
    val watchAppPaletteId: Int, // Positive for user-defined, negative for system/hard-coded this is the id sent to the app to notify something has changed
    val uniqueId: String, // uuid for sharing palette via profiles, we will keep the old one if we already have the uuid
    val name: String,
    val colors: List<RGBColor>,
    val isEditable: Boolean = true, // System palettes are not editable by the user
    val mappingMode: PaletteMappingMode = PaletteMappingMode.NEAREST_NEIGHBOR,
) {
    fun allColoursForApp(): List<RGBColor> {
        val targetSize = 64
        val paddingColor = RGBColor(0, 0, 0) // Integer for Black

        // Start with a mutable list of all colors
        val finalPalette = colors.toMutableList()

        // Truncate the list if it's too long
        while (finalPalette.size > targetSize) {
            finalPalette.removeLast()
        }

        // Pad the list with black if it's too short
        while (finalPalette.size < targetSize) {
            finalPalette.add(paddingColor)
        }

        return finalPalette
    }
}
