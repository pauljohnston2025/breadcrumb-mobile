package com.paul.protocol.todevice

import com.paul.domain.ColourPalette
import com.paul.infrastructure.service.ColourPaletteConverter
import com.paul.infrastructure.service.ColourPaletteConverter.Companion.isCloseToWhite
import com.paul.infrastructure.web.TileType
import okio.ByteString.Companion.toByteString
import kotlin.random.Random

class MapTile(
    private val x: Int,
    private val y: Int,
    private val z: Int,
    private val pixelArray: IntArray
) {

    fun colourString(tileType: TileType, selectedColourPalette: ColourPalette): String {
        return when (tileType) {
            TileType.TILE_DATA_TYPE_64_COLOUR -> {
                // Delegate the entire complex conversion to our centralized converter.
                ColourPaletteConverter.convertPixelArrayTo64ColourString(pixelArray, selectedColourPalette)
            }
            TileType.TILE_DATA_TYPE_BASE64_FULL_COLOUR -> fullColourStringBase64()
            TileType.TILE_DATA_TYPE_BLACK_AND_WHITE -> blackAndWhiteColourString()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun fullColourStringBase64(): String {
        // We can build the byte array directly from the IntArray.
        val bytes = ByteArray(pixelArray.size * 3)
        var i = 0
        for (pixel in pixelArray) {
            bytes[i++] = (pixel shr 16 and 0xFF).toByte() // R
            bytes[i++] = (pixel shr 8 and 0xFF).toByte()  // G
            bytes[i++] = (pixel and 0xFF).toByte()        // B
        }
        return bytes.toByteString().base64()
    }

    fun blackAndWhiteColourString(): String {

        var str = ""
        var colourByte = 0x00
        var bit = 0
        for (pixel in pixelArray) {
            // 1. Extract RGB components directly from the Int.
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)

            // 2. Use our centralized logic to determine the bit value.
            val colourBit = if (ColourPaletteConverter.isCloseToWhite(r, g, b)) 1 else 0

            colourByte = colourByte or (colourBit shl bit)
            bit++
            if (bit >= 6) {
                val byteVal = ((colourByte or 0x40) and 0x7F).toByte()
                val char = byteArrayOf(byteVal).decodeToString()
                str += char
                bit = 0
                colourByte = 0x00
            }
        }
        // add the last byte in
        if (bit != 0) {
            val byteVal = ((colourByte or 0x40) and 0x7F).toByte()
            val char = byteArrayOf(byteVal).decodeToString()
            str += char
        }

        return str
    }
}
