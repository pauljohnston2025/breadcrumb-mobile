package com.paul.protocol.todevice

import com.paul.domain.ColourPalette
import com.paul.infrastructure.service.ColourPaletteConverter
import com.paul.infrastructure.web.TileType
import okio.ByteString.Companion.toByteString
import kotlin.random.Random

data class Colour(
    // to be able to send over bluetooth fast enough
    // colour byes are sent as 8 bit colour
    // 2 bit alpha,
    // 2 bits red
    // 2 bits green
    // 2 bits blue
    // so valid colour values are, we might pick an entirely new colour palate in the future,
    // but this should be ok for proof of concept
    val red: UByte,
    val green: UByte,
    val blue: UByte,
) {
    fun as64Colour(selectedColourPalette: ColourPalette): Byte {
        return ColourPaletteConverter.convertColourToPalette(this, selectedColourPalette)
//        // not the best conversion, but ok for now
//        val colour =  ((Math.round(red.toInt() / 255.0f) * 3) shl 4) or
//                ((Math.round(green.toInt() / 255.0f) * 3) shl 2) or
//                (Math.round(blue.toInt() / 255.0f) * 3)
////        Napier.d("red is: " + red.toInt());
////        Napier.d("red is: " + red.toUInt());
////        Napier.d("colour is: " + colour);
//        return colour.toByte()
    }

    fun isCloseToWhite(): Boolean {
        return ColourPaletteConverter.isCloseToWhite(this)
    }

    fun as24BitColour(): List<UByte> {
        return listOf(red, green, blue)
    }

    fun asPackedColour(): Int {
        // not the best conversion, but ok for now
        val colour = (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
        return colour
    }

    companion object {
        fun random(): Colour {
            return Colour(
                Random.nextInt(0, 255).toUByte(),
                Random.nextInt(0, 255).toUByte(),
                Random.nextInt(0, 255).toUByte()
            )
        }
    }
}

class MapTile(
    private val x: Int,
    private val y: Int,
    private val z: Int,
    private val pixelData: List<Colour>
) {

    fun colourString(tileType: TileType, selectedColourPalette: ColourPalette): String {
        return when (tileType) {
            TileType.TILE_DATA_TYPE_64_COLOUR -> colourString64Colour(selectedColourPalette)
            TileType.TILE_DATA_TYPE_BASE64_FULL_COLOUR -> fullColourStringBase64()
            TileType.TILE_DATA_TYPE_BLACK_AND_WHITE -> blackAndWhiteColourString()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun fullColourStringBase64(): String {
        var bytes = mutableListOf<UByte>()
        // testing data
        for (colour in pixelData) {
            bytes.addAll(colour.as24BitColour())
        }
        return bytes.toUByteArray().toByteArray().toByteString().base64()
    }

    fun blackAndWhiteColourString(): String {

        var str = ""
        var colourByte = 0x00
        var bit = 0
        for (colour in pixelData) {
            val colourBit = if (colour.isCloseToWhite()) 1 else 0
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

    fun colourString64Colour(selectedColourPalette: ColourPalette): String {
        // monkey c is a really annoying encoding, it does not allow sending a raw byte array
        // you can send strings, but they have to be valid utf8, fortunately we are not using the
        // top 2 bits in our encoding yet (so all our values are in the ascii range)
        // if we want to use the top 2 bits, we might have to send it as a whole heap of ints,
        // and deal with the overhead another way
        // (base64 encoding suggested, but that has its own overhead of ~33-37%)
        // extra byte per 4 byte sent is only 25% overhead
        // not using the top 2 bits has a 25% overhead too, but with 1 less byte for 4 colours sent

        var str = "";
        // testing data
        for (colour in pixelData) {
            val colourByte = colour.as64Colour(selectedColourPalette)
//            Napier.d("colour byte is: " + colourByte.toInt())
            // we also cannot send all 0's since its the null terminator
            // so we will set the second highest bit
            val byteVal = ((colourByte.toInt() or 0x40) and 0x7F).toByte()
            val char = byteArrayOf(byteVal).decodeToString()
            str += char
        }
        return str
    }
}
