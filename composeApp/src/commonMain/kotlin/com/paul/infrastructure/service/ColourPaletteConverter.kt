package com.paul.infrastructure.service

import com.paul.domain.ColourPalette
import com.paul.domain.PaletteMappingMode
import com.paul.domain.RGBColor
import com.paul.protocol.todevice.Colour
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Data class to hold CIELAB values
private data class LabColor(val l: Double, val a: Double, val b: Double)

class ColourPaletteConverter {
    companion object {

        private val blackAndWhitePalette = ColourPalette(
            watchAppPaletteId = 0,
            uniqueId = "internal-bw-palette",
            name = "Internal B&W",
            colors = listOf(RGBColor(0, 0, 0), RGBColor(255, 255, 255)),
            mappingMode = PaletteMappingMode.ORDERED_BY_BRIGHTNESS,
            isEditable = false
        )

        //<editor-fold desc="Color Conversion and Distance Logic">

        private fun brightness(r: Int, g: Int, b: Int): Float {
            return 0.299f * r + 0.587f * g + 0.114f * b
        }

        fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
            val rMean = (r1 + r2) / 2f
            val rDiff = r1 - r2
            val gDiff = g1 - g2
            val bDiff = b1 - b2
            val weightR = 2 + rMean / 256
            val weightG = 4
            val weightB = 2 + (255 - rMean) / 256
            return (weightR * rDiff * rDiff + weightG * gDiff * gDiff + weightB * bDiff * bDiff)
        }

        // --- CIELAB Conversion and Distance Calculation ---

        private fun rgbToLab(r: Int, g: Int, b: Int): LabColor {
            // Step 1: sRGB to linear RGB
            var R = r / 255.0
            var G = g / 255.0
            var B = b / 255.0

            R = if (R > 0.04045) ((R + 0.055) / 1.055).pow(2.4) else R / 12.92
            G = if (G > 0.04045) ((G + 0.055) / 1.055).pow(2.4) else G / 12.92
            B = if (B > 0.04045) ((B + 0.055) / 1.055).pow(2.4) else B / 12.92

            // Step 2: linear RGB to XYZ
            val X = R * 0.4124564 + G * 0.3575761 + B * 0.1804375
            val Y = R * 0.2126729 + G * 0.7151522 + B * 0.0721750
            val Z = R * 0.0193339 + G * 0.1191920 + B * 0.9503041

            // Step 3: XYZ to CIELAB
            var xr = X / 0.95047 // D65 reference white
            var yr = Y / 1.00000
            var zr = Z / 1.08883

            xr = if (xr > 0.008856) xr.pow(1.0 / 3.0) else (7.787 * xr) + (16.0 / 116.0)
            yr = if (yr > 0.008856) yr.pow(1.0 / 3.0) else (7.787 * yr) + (16.0 / 116.0)
            zr = if (zr > 0.008856) zr.pow(1.0 / 3.0) else (7.787 * zr) + (16.0 / 116.0)

            val L = (116.0 * yr) - 16.0
            val a = 500.0 * (xr - yr)
            val b_ = 200.0 * (yr - zr)

            return LabColor(L, a, b_)
        }

        private fun cielabDistance(lab1: LabColor, lab2: LabColor): Double {
            // Delta E (CIE76) formula
            val dL = lab1.l - lab2.l
            val da = lab1.a - lab2.a
            val db = lab1.b - lab2.b
            return sqrt(dL * dL + da * da + db * db)
        }

        //</editor-fold>

        //<editor-fold desc="Palette Index Finding Logic">

        fun findNearestColorIndex(red: Int, green: Int, blue: Int, palette: List<RGBColor>): Int {
            var minDistance = Float.MAX_VALUE
            var nearestIndex = 0
            for (i in palette.indices) {
                val paletteColor = palette[i]
                val distance = colorDistance(red, green, blue, paletteColor.r, paletteColor.g, paletteColor.b)
                if (distance < minDistance) {
                    minDistance = distance
                    nearestIndex = i
                }
            }
            return nearestIndex
        }

        private fun findIndexOrderedByBrightness(red: Int, green: Int, blue: Int, palette: List<RGBColor>): Int {
            if (palette.isEmpty()) return 0
            val indexedPalette = palette.mapIndexed { index, color -> IndexedValue(index, color) }
            val sortedPalette = indexedPalette.sortedBy { brightness(it.value.r, it.value.g, it.value.b) }
            val inputBrightness = brightness(red, green, blue)
            val normalizedBrightness = inputBrightness / 255f
            val scaledIndex = normalizedBrightness * (sortedPalette.size - 1)
            val chosenIndexInSorted = scaledIndex.roundToInt().coerceIn(0, sortedPalette.size - 1)
            return sortedPalette[chosenIndexInSorted].index
        }

        private fun findNearestColorIndexCIELAB(red: Int, green: Int, blue: Int, palette: List<RGBColor>): Int {
            if (palette.isEmpty()) return 0
            val inputLab = rgbToLab(red, green, blue)
            val paletteLab = palette.map { rgbToLab(it.r, it.g, it.b) } // Convert entire palette

            var minDistance = Double.MAX_VALUE
            var nearestIndex = 0
            for (i in paletteLab.indices) {
                val distance = cielabDistance(inputLab, paletteLab[i])
                if (distance < minDistance) {
                    minDistance = distance
                    nearestIndex = i
                }
            }
            return nearestIndex
        }

        //</editor-fold>

        //<editor-fold desc="Public API">

        fun rgbTo6Bit(red: Int, green: Int, blue: Int, colourPalette: ColourPalette): Byte {
            val paletteIndex = when (colourPalette.mappingMode) {
                PaletteMappingMode.NEAREST_NEIGHBOR -> findNearestColorIndex(red, green, blue, colourPalette.colors)
                PaletteMappingMode.ORDERED_BY_BRIGHTNESS -> findIndexOrderedByBrightness(red, green, blue, colourPalette.colors)
                PaletteMappingMode.CIELAB -> findNearestColorIndexCIELAB(red, green, blue, colourPalette.colors)
            }

            if (paletteIndex >= 64) {
                return 63
            }
            return paletteIndex.toByte()
        }

        fun convertColourToPalette(colour: Colour, palette: ColourPalette): Byte {
            return rgbTo6Bit(colour.red.toInt(), colour.green.toInt(), colour.blue.toInt(), palette)
        }

        fun isCloseToWhite(colour: Colour): Boolean {
            val paletteIndex = findIndexOrderedByBrightness(
                colour.red.toInt(),
                colour.green.toInt(),
                colour.blue.toInt(),
                blackAndWhitePalette.colors
            )
            return paletteIndex == 1
        }
        //</editor-fold>
    }
}