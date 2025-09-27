package com.paul.domain

import kotlinx.serialization.Serializable

@Serializable
data class ColourPalette(
    val watchAppPaletteId: Int, // Positive for user-defined, negative for system/hard-coded this is the id sent to the app to notify something has changed
    val uniqueId: String, // uuid for sharing palette via profiles, we will keep the old one if we already have the uuid
    val name: String,
    val colors: List<RGBColor>,
    val isEditable: Boolean = true // System palettes are not editable by the user
)
