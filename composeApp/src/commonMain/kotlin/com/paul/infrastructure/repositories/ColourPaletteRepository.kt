package com.paul.infrastructure.repositories

import com.paul.domain.ColourPalette
import com.paul.domain.RGBColor
import com.paul.infrastructure.web.KtorClient
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ColourPaletteRepository(
    private val tileRepo: ITileRepository, // the one running for the maps page (we update the webserver one through web calls)
) {

    companion object {
        val PALETTE_ID_KEY = "CURRENT_COLOUR_PALETTE_ID" // Stores the ID of the selected palette
        val CUSTOM_PALETTES_KEY = "CUSTOM_COLOUR_PALETTES" // Stores custom palettes as JSON
        val settings: Settings = Settings()

        // System palettes (negative IDs)
        val opentopoPalette = ColourPalette(
            id = -1, // Use -1 for the original hardcoded palette
            name = "World Topo Map",
            colors = listOf(
                // Greens (Emphasis) - 22 colors
                RGBColor(61, 179, 61),       // Vibrant Green
                RGBColor(102, 179, 102),      // Medium Green
                RGBColor(153, 204, 153),      // Light Green
                RGBColor(0, 102, 0),         // Dark Green
                RGBColor(128, 179, 77),      // Slightly Yellowish Green
                RGBColor(77, 179, 128),      // Slightly Bluish Green
                RGBColor(179, 179, 179),       // Pale Green
                RGBColor(92, 128, 77),      // Olive Green
                RGBColor(148, 209, 23),
                RGBColor(107, 142, 35),  // OliveDrab
                RGBColor(179, 230, 0),        // Lime Green
                RGBColor(102, 179, 0),        // Spring Green
                RGBColor(77, 204, 77),      // Bright Green
                RGBColor(128, 153, 128),      // Grayish Green
                RGBColor(153, 204, 153),      // Soft Green
                RGBColor(0, 128, 0),         // Forest Green
                RGBColor(34, 139, 34),    // ForestGreen
                RGBColor(50, 205, 50),    // LimeGreen
                RGBColor(144, 238, 144),  // LightGreen
                RGBColor(0, 100, 0),       // DarkGreen
                RGBColor(60, 179, 113),     // Medium Sea Green
                RGBColor(46, 139, 87),      // SeaGreen

                // Reds - 8 colors
                RGBColor(230, 0, 0),         // Bright Red
                RGBColor(204, 102, 102),      // Light Red (Pink)
                RGBColor(153, 0, 0),         // Dark Red
                RGBColor(230, 92, 77),      // Coral Red
                RGBColor(179, 0, 38),         // Crimson
                RGBColor(204, 102, 102),      // Rose
                RGBColor(255, 0, 0),     // Pure Red
                RGBColor(255, 69, 0),    // RedOrange

                // Blues - 8 colors
                RGBColor(0, 0, 230),         // Bright Blue
                RGBColor(102, 102, 204),      // Light Blue
                RGBColor(0, 0, 153),         // Dark Blue
                RGBColor(102, 153, 230),      // Sky Blue
                RGBColor(38, 0, 179),         // Indigo
                RGBColor(77, 128, 179),      // Steel Blue
                RGBColor(0, 0, 255),       // Pure Blue
                RGBColor(0, 191, 255),      // DeepSkyBlue
                RGBColor(151, 210, 227), // ocean blue

                // Yellows - 6 colors
                RGBColor(230, 230, 0),        // Bright Yellow
                RGBColor(204, 204, 102),      // Light Yellow
                RGBColor(153, 153, 0),        // Dark Yellow (Gold)
                RGBColor(179, 153, 77),      // Mustard Yellow
                RGBColor(255, 255, 0),   // Pure Yellow
                RGBColor(255, 215, 0),   // Gold

                // Oranges - 6 colors
                RGBColor(230, 115, 0),        // Bright Orange
                RGBColor(204, 153, 102),      // Light Orange
                RGBColor(153, 77, 0),         // Dark Orange
                RGBColor(179, 51, 0),         // Burnt Orange
                RGBColor(255, 165, 0),    // Orange
                RGBColor(255, 140, 0),    // DarkOrange

                // Purples - 6 colors
                RGBColor(230, 0, 230),        // Bright Purple
                RGBColor(204, 102, 204),      // Light Purple
                RGBColor(153, 0, 153),        // Dark Purple
                RGBColor(230, 153, 230),      // Lavender
                RGBColor(128, 0, 128),   // Purple
                RGBColor(75, 0, 130),   // Indigo

                // Neutral/Grayscale - 4 colors
                RGBColor(242, 242, 242),      // White
                //        RGBColor(179, 179, 179),       // Light Gray
                RGBColor(77, 77, 77),         // Dark Gray
                RGBColor(0, 0, 0),         // Black

                // manually picked to match map tiles
                RGBColor(246, 230, 98), // road colours (yellow)
                RGBColor(194, 185, 108), // slightly darker yellow road
                RGBColor(214, 215, 216), // some mountains (light grey)
                RGBColor(213, 237, 168), // some greenery that was not a nice colour
            ),
            isEditable = false
        )

        // see https://developer.garmin.com/connect-iq/user-experience-guidelines/incorporating-the-visual-design-and-product-personalities/
        val mips64Palette = ColourPalette(
            id = -2,
            name = "MIP 64",
            colors = listOf(
                RGBColor(0xFF, 0xFF, 0xFF),
                RGBColor(0xFF, 0xFF, 0xAA),
                RGBColor(0xFF, 0xFF, 0x55),
                RGBColor(0xFF, 0xFF, 0x00),
                RGBColor(0xFF, 0xAA, 0xFF),
                RGBColor(0xFF, 0xAA, 0xAA),
                RGBColor(0xFF, 0xAA, 0x55),
                RGBColor(0xFF, 0xAA, 0x00),
                RGBColor(0xFF, 0x55, 0xFF),
                RGBColor(0xFF, 0x55, 0xAA),
                RGBColor(0xFF, 0x55, 0x55),
                RGBColor(0xFF, 0x55, 0x00),
                RGBColor(0xFF, 0x00, 0xFF),
                RGBColor(0xFF, 0x00, 0xAA),
                RGBColor(0xFF, 0x00, 0x55),
                RGBColor(0xFF, 0x00, 0x00),
                RGBColor(0xAA, 0xFF, 0xFF),
                RGBColor(0xAA, 0xFF, 0xAA),
                RGBColor(0xAA, 0xFF, 0x55),
                RGBColor(0xAA, 0xFF, 0x00),
                RGBColor(0xAA, 0xAA, 0xFF),
                RGBColor(0xAA, 0xAA, 0xAA),
                RGBColor(0xAA, 0xAA, 0x55),
                RGBColor(0xAA, 0xAA, 0x00),
                RGBColor(0xAA, 0x55, 0xFF),
                RGBColor(0xAA, 0x55, 0xAA),
                RGBColor(0xAA, 0x55, 0x55),
                RGBColor(0xAA, 0x55, 0x00),
                RGBColor(0xAA, 0x00, 0xFF),
                RGBColor(0xAA, 0x00, 0xAA),
                RGBColor(0xAA, 0x00, 0x55),
                RGBColor(0xAA, 0x00, 0x00),
                RGBColor(0x55, 0xFF, 0xFF),
                RGBColor(0x55, 0xFF, 0xAA),
                RGBColor(0x55, 0xFF, 0x55),
                RGBColor(0x55, 0xFF, 0x00),
                RGBColor(0x55, 0xAA, 0xFF),
                RGBColor(0x55, 0xAA, 0xAA),
                RGBColor(0x55, 0xAA, 0x55),
                RGBColor(0x55, 0xAA, 0x00),
                RGBColor(0x55, 0x55, 0xFF),
                RGBColor(0x55, 0x55, 0xAA),
                RGBColor(0x55, 0x55, 0x55),
                RGBColor(0x55, 0x55, 0x00),
                RGBColor(0x55, 0x00, 0xFF),
                RGBColor(0x55, 0x00, 0xAA),
                RGBColor(0x55, 0x00, 0x55),
                RGBColor(0x55, 0x00, 0x00),
                RGBColor(0x00, 0xFF, 0xFF),
                RGBColor(0x00, 0xFF, 0xAA),
                RGBColor(0x00, 0xFF, 0x55),
                RGBColor(0x00, 0xFF, 0x00),
                RGBColor(0x00, 0xAA, 0xFF),
                RGBColor(0x00, 0xAA, 0xAA),
                RGBColor(0x00, 0xAA, 0x55),
                RGBColor(0x00, 0xAA, 0x00),
                RGBColor(0x00, 0x55, 0xFF),
                RGBColor(0x00, 0x55, 0xAA),
                RGBColor(0x00, 0x55, 0x55),
                RGBColor(0x00, 0x55, 0x00),
                RGBColor(0x00, 0x00, 0xFF),
                RGBColor(0x00, 0x00, 0xAA),
                RGBColor(0x00, 0x00, 0x55),
                RGBColor(0x00, 0x00, 0x00),
            ),
            isEditable = false
        )

        // see https://developer.garmin.com/connect-iq/user-experience-guidelines/incorporating-the-visual-design-and-product-personalities/
        val fr4555Palette = ColourPalette(
            id = -3,
            name = "Forerunner 45 and 55",
            colors = listOf(
                RGBColor(0xFF, 0xFF, 0xFF),
                RGBColor(0xFF, 0xFF, 0x00),
                RGBColor(0xFF, 0x00, 0xFF),
                RGBColor(0xFF, 0x00, 0x00),
                RGBColor(0x00, 0xFF, 0xFF),
                RGBColor(0x00, 0xFF, 0x00),
                RGBColor(0x00, 0x00, 0xFF),
                RGBColor(0x00, 0x00, 0x00),
            ),
            isEditable = false
        )
        val systemPalettes = listOf(
            opentopoPalette,
            mips64Palette,
            fr4555Palette
        )

        fun getSelectedPaletteOnStart(): ColourPalette {
            val selectedPaletteId = settings.getInt(PALETTE_ID_KEY, opentopoPalette.id)
            val customPalettes = getCustomPalettesOnStart()
            return (systemPalettes + customPalettes).find { it.id == selectedPaletteId }
                ?: opentopoPalette // Fallback to default if ID not found
        }

        fun getCustomPalettesOnStart(): List<ColourPalette> {
            val customPalettesJson = settings.getStringOrNull(CUSTOM_PALETTES_KEY)
            return customPalettesJson?.let {
                try {
                    Json.decodeFromString<List<ColourPalette>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    private val client = KtorClient.client // Get the singleton client instance

    private val _currentColourPalette: MutableStateFlow<ColourPalette> =
        MutableStateFlow(getSelectedPaletteOnStart())
    val currentColourPaletteFlow: StateFlow<ColourPalette> = _currentColourPalette.asStateFlow()

    private val _availableColourPalettes: MutableStateFlow<List<ColourPalette>> = MutableStateFlow(
        systemPalettes + getCustomPalettesOnStart()
    )
    val availableColourPalettesFlow: StateFlow<List<ColourPalette>> =
        _availableColourPalettes.asStateFlow()

    suspend fun updateCurrentColourPalette(palette: ColourPalette) {
        // todo check if the server is enabled, the ui is only show if enabled so this should be fine for now
        if (true) {
            // tell the webserver service that it changed too
            // cannot use resources plugin, as the nested colour params are serialised incorrectly
            try {
                val response = client.post("/changeColourPalette") {
                    contentType(ContentType.Application.Json)
                    setBody(palette)
                }
                if (!response.status.isSuccess()) {
                    // Log the failure
                    Napier.e("Failed to update palette on server. Status: ${response.status}")
                    return
                }
            } catch (e: Exception) {
                // This will catch serialization errors or network issues
                Napier.e("Error sending palette to server", e)
                throw e
            }
        }
        tileRepo.setCurrentPalette(palette)

        _currentColourPalette.emit(palette)
        settings.putInt(PALETTE_ID_KEY, palette.id)
    }

    suspend fun addOrUpdateCustomPalette(palette: ColourPalette) {
        val currentCustomPalettes = getCustomPalettesOnStart().toMutableList()
        val originalId = palette.id

        // Remove the old version if it's an update
        if (originalId > 0) {
            currentCustomPalettes.removeIf { it.id == originalId }
        }

        // Assign a new, unique, smallest possible ID and add it
        val newId = findNextAvailableId(currentCustomPalettes, originalId)
        val newPalette = palette.copy(id = newId)
        currentCustomPalettes.add(newPalette)

        // Save the updated list
        settings.putString(CUSTOM_PALETTES_KEY, Json.encodeToString(currentCustomPalettes))
        _availableColourPalettes.emit(systemPalettes + currentCustomPalettes.sortedBy { it.id })

        updateCurrentColourPalette(newPalette)
    }


    suspend fun removeCustomPalette(palette: ColourPalette) {
        if (!palette.isEditable) return // Cannot remove system palettes

        val currentCustomPalettes = getCustomPalettesOnStart().toMutableList()
        currentCustomPalettes.removeIf { it.id == palette.id }

        settings.putString(CUSTOM_PALETTES_KEY, Json.encodeToString(currentCustomPalettes))
        _availableColourPalettes.emit(systemPalettes + currentCustomPalettes)

        // If the removed palette was the current one, switch to default system palette
        if (_currentColourPalette.value.id == palette.id) {
            updateCurrentColourPalette(opentopoPalette)
        }
    }

    fun getPaletteById(id: Int): ColourPalette? {
        return (_availableColourPalettes.value).find { it.id == id }
    }

    private fun findNextAvailableId(currentCustomPalettes: List<ColourPalette>, originalId: Int): Int {
        // Find the maximum ID from the remaining palettes in the list.
        val maxExistingId = currentCustomPalettes.map { it.id }.maxOrNull() ?: 0

        // The new ID must be greater than the highest ID still in the list,
        // and also greater than the ID of the palette that was just removed.
        return maxOf(maxExistingId, originalId) + 1
    }
}