package com.paul.domain

import com.paul.protocol.todevice.Colour
import kotlinx.serialization.Serializable

@Serializable
data class ColourPalette(
    val id: Int, // Positive for user-defined, negative for system/hard-coded
    val name: String,
    val colors: List<RGBColor>,
    val isEditable: Boolean = true // System palettes are not editable by the user
)
