package com.paul.infrastructure.service

import com.paul.domain.RGBColor
import com.paul.protocol.todevice.Colour

class ColourPaletteConverter {
    companion object {

        fun findNearestColorIndex(red: Int, green: Int, blue: Int, palette: List<RGBColor>): Int {
            var minDistance = Float.MAX_VALUE
            var nearestIndex = 0

            for (i in palette.indices) {
                val paletteColor = palette[i]
                val distance =
                    colorDistance(red, green, blue, paletteColor.r, paletteColor.g, paletteColor.b)

                if (distance < minDistance) {
                    minDistance = distance
                    nearestIndex = i
                }
            }
            return nearestIndex
        }

        fun colorDistance(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Float {
            val rMean = (r1 + r2) / 2f
            val rDiff = r1 - r2
            val gDiff = g1 - g2
            val bDiff = b1 - b2

            val weightR = 2 + rMean / 256
            val weightG = 4
            val weightB = 2 + (255 - rMean) / 256

            return (weightR * rDiff * rDiff + weightG * gDiff * gDiff + weightB * bDiff * bDiff) as Float
        }

        fun rgbTo6Bit(red: Int, green: Int, blue: Int, colorPalette: List<RGBColor>): Byte {
            val paletteIndex = findNearestColorIndex(red, green, blue, colorPalette)
            if (paletteIndex >= 64) {
                // we only support the first 64 colours in the palette
                return 63
            }
            return paletteIndex.toByte()
        }

        fun convertColourToPalette(colour: Colour, palette: List<RGBColor>): Byte {
            val packedColor =
                rgbTo6Bit(colour.red.toInt(), colour.green.toInt(), colour.blue.toInt(), palette)
//        Napier.d("Packed color (6-bit): 0x${String.format("%02X", packedColor)}")
            return packedColor
        }

        fun isCloseToWhite(colour: Colour): Boolean {
            // we want to consider even very bright grays as white, so the bottom approach is slightly better for stamen toner maps
//        val white = RGBColor(255, 255, 255)
//        val black = RGBColor(0, 0, 0)
//        val bWColorPalette = listOf(black, white);
//        val paletteIndex = findNearestColorIndex(colour.red.toInt(), colour.green.toInt(), colour.blue.toInt(), bWColorPalette)
//        return paletteIndex == 1

            return colour.red.toInt() > 200 && colour.green.toInt() > 200 && colour.blue.toInt() > 200
        }
    }
}


