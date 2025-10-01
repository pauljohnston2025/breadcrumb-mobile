package com.paul.infrastructure.service

import com.paul.domain.ColourPalette
import com.paul.domain.PaletteMappingMode
import com.paul.domain.RGBColor
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

        /**
         * A version of isCloseToWhite that operates directly on RGB integers.
         * This is more efficient as it avoids object creation.
         */
        fun isCloseToWhite(r: Int, g: Int, b: Int): Boolean {
            // This reuses your existing brightness mapping logic
            val paletteIndex = findIndexOrderedByBrightness(r, g, b, blackAndWhitePalette.colors)
            // Assuming white is the brighter color, its index in the sorted list will be 1.
            return paletteIndex == 1
        }

        /**
         * Extracts the dominant colors from a tile's pixel data using a fast, frequency-based
         * quantization algorithm. This is the refactored version of your createPaletteFromViewport logic.
         *
         * @param pixelArray The raw ARGB pixel data from the tile.
         * @param maxColors The maximum number of colors to return in the palette.
         * @param precisionShift The bit shift to apply for color grouping (3 is a good default for 5-bit channels).
         * @return A List of RGBColor objects representing the most common colors.
         */
        fun extractDominantColors(
            pixelArray: IntArray,
            maxColors: Int = 64,
            precisionShift: Int = 3
        ): List<RGBColor> {
            if (pixelArray.isEmpty()) return emptyList()

            val colorFrequencies = mutableMapOf<Int, Int>()

            for (color in pixelArray) {
                // Extract, reduce, and repack color channels into a single Int key.
                val r = (color shr 16 and 0xFF) shr precisionShift
                val g = (color shr 8 and 0xFF) shr precisionShift
                val b = (color and 0xFF) shr precisionShift
                val reducedColor = (r shl (precisionShift * 2)) or (g shl precisionShift) or b

                colorFrequencies[reducedColor] = (colorFrequencies[reducedColor] ?: 0) + 1
            }

            // Sort by frequency and take the top colors
            val topColors = colorFrequencies.entries
                .sortedByDescending { it.value }
                .take(maxColors)
                .map { it.key }

            // Convert the reduced-precision colors back to full RGBColor objects
            return topColors.map { reducedColor ->
                // Unpack and scale the reduced-bit channels back up to 8-bit.
                val r = (reducedColor shr (precisionShift * 2)) shl precisionShift
                val g = (reducedColor shr precisionShift and ((1 shl precisionShift) - 1)) shl precisionShift
                val b = (reducedColor and ((1 shl precisionShift) - 1)) shl precisionShift
                RGBColor(r, g, b)
            }
        }

        /**
         * The new "master" function. Converts an entire tile's pixel data to the 64-colour string
         * based on the selected palette's mapping mode. This is much more efficient as it performs
         * setup (like LUT generation) only once per tile.
         *
         * @param pixelArray The raw pixel data of the tile.
         * @param targetPalette The destination ColourPalette.
         * @return The final encoded string for the device.
         */
        fun convertPixelArrayTo64ColourString(
            pixelArray: IntArray,
            targetPalette: ColourPalette
        ): String {
            val indices = when (targetPalette.mappingMode) {
                PaletteMappingMode.PALETTE_REMAP -> {
                    // 1. Dynamically get the source palette from the tile itself.
//                    val sourcePalette = extractDominantColors(pixelArray, maxColors = 64) // Let's use 64 to be safe.
                    val sourcePalette = extractDominantColors(pixelArray, maxColors = targetPalette.colors.size)
                    if (sourcePalette.isEmpty()) return ""

                    // 2. Generate the Lookup Table (LUT) mapping the source to the target.
                    val remapLut = generateRemapLut(sourcePalette, targetPalette.colors)

                    // 3. Convert all pixels using the LUT for maximum performance.
                    val sourceLabPalette = sourcePalette.map { rgbToLab(it.r, it.g, it.b) }
                    pixelArray.map { pixel ->
                        val r = (pixel shr 16 and 0xFF)
                        val g = (pixel shr 8 and 0xFF)
                        val b = (pixel and 0xFF)
                        val pixelLab = rgbToLab(r, g, b)

                        // Find the nearest color in our extracted source palette...
                        val closestSourceColor = sourcePalette[findClosestLabIndex(pixelLab, sourceLabPalette)]
                        // ...and get the final mapped index from the LUT.
                        remapLut[closestSourceColor] ?: 0
                    }
                }
                else -> {
                    // Handle all other modes (NEAREST_NEIGHBOR, CIELAB, etc.) on a per-pixel basis.
                    pixelArray.map { pixel ->
                        val r = (pixel shr 16 and 0xFF)
                        val g = (pixel shr 8 and 0xFF)
                        val b = (pixel and 0xFF)
                        // This reuses your existing rgbTo6Bit logic internally.
                        findPaletteIndex(r, g, b, targetPalette)
                    }
                }
            }

            // 4. Encode the final list of indices into the Monkey C compatible string.
            return indices.joinToString("") { paletteIndex ->
                val byteVal = ((paletteIndex.toInt() or 0x40) and 0x7F).toByte()
                byteArrayOf(byteVal).decodeToString()
            }
        }

        // Helper to find the index of the closest color in a pre-calculated Lab list.
        private fun findClosestLabIndex(targetLab: LabColor, labPalette: List<LabColor>): Int {
            var minDistance = Double.MAX_VALUE
            var nearestIndex = 0
            for (i in labPalette.indices) {
                val distance = cielabDistance(targetLab, labPalette[i])
                if (distance < minDistance) {
                    minDistance = distance
                    nearestIndex = i
                }
            }
            return nearestIndex
        }

        // Helper to generate the remapping LUT
        private fun generateRemapLut(source: List<RGBColor>, destination: List<RGBColor>): Map<RGBColor, Byte> {
            if (source.isEmpty() || destination.isEmpty()) return emptyMap()

            val sortedSource = source.sortedBy { rgbToLab(it.r, it.g, it.b).l }
            val indexedDestination = destination.mapIndexed { index, color -> IndexedValue(index, color) }
            val sortedDestination = indexedDestination.sortedBy { rgbToLab(it.value.r, it.value.g, it.value.b).l }

            val lut = mutableMapOf<RGBColor, Byte>()
            val sourceSize = sortedSource.size
            val destSize = sortedDestination.size

            for (i in sortedSource.indices) {
                val sourceColor = sortedSource[i]
                val destIndex = if (sourceSize > 1) (i.toFloat() / (sourceSize - 1) * (destSize - 1)).roundToInt() else 0
                val mappedOriginalIndex = sortedDestination[destIndex.coerceIn(0, destSize - 1)].index
                lut[sourceColor] = mappedOriginalIndex.toByte()
            }
            return lut
        }

        // A single internal function to find an index based on mapping mode.
        private fun findPaletteIndex(r: Int, g: Int, b: Int, palette: ColourPalette): Byte {
            val index = when (palette.mappingMode) {
                PaletteMappingMode.NEAREST_NEIGHBOR -> findNearestColorIndex(r, g, b, palette.colors)
                PaletteMappingMode.ORDERED_BY_BRIGHTNESS -> findIndexOrderedByBrightness(r, g, b, palette.colors)
                PaletteMappingMode.CIELAB -> findNearestColorIndexCIELAB(r, g, b, palette.colors)
                PaletteMappingMode.PALETTE_REMAP -> 0 // This mode should be handled by the master function.
            }
            return if (index >= 64) 63 else index.toByte()
        }

    }
}