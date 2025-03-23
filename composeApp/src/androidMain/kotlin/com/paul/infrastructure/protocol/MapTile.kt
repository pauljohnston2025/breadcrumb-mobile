package com.paul.infrastructure.protocol

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
)
{
    fun asCharColour(): Byte {
        return HardCodedColourPalette().convertColourToPalette(this)
//        // not the best conversion, but ok for now
//        val colour =  ((Math.round(red.toInt() / 255.0f) * 3) shl 4) or
//                ((Math.round(green.toInt() / 255.0f) * 3) shl 2) or
//                (Math.round(blue.toInt() / 255.0f) * 3)
////        Log.d("stdout","red is: " + red.toInt());
////        Log.d("stdout","red is: " + red.toUInt());
////        Log.d("stdout","colour is: " + colour);
//        return colour.toByte()
    }

    fun asPackedColour(): Int {
        // not the best conversion, but ok for now
        val colour = (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
        return colour
    }

    companion object {
        fun random() : Colour {
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
    private val pixelData: List<Colour>
) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_MAP_TILE
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>()
        // todo optimise this even further to manually packed array, each int serialises as a minimum of 5 bytes
        data.add(x);
        data.add(y);
        data.add(colourString())
        return data
    }

    fun colourString(): String {
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
        for (colour in pixelData.chunked(2)) {
            val colourByte1 = colour[0].asCharColour()
            val colourByte2 = colour[1].asCharColour()
//            Log.d("stdout","colour byte is: " + colourByte.toInt())
            // we also cannot send all 0's since its the null terminator
            // so we will set the second highest bit
            val colourByte = ((colourByte1.toInt() and 0x07) shl 3) or (colourByte2.toInt() and 0x07)
            val byteVal = ((colourByte or 0x40) and 0x7F).toByte()
            val char = byteArrayOf(byteVal).decodeToString()
            str += char
        }
        return str
    }

    fun colourList(): List<Int> {
        val res = mutableListOf<Int>();
        for (colour in pixelData) {
            val colourInt = colour.asPackedColour()
//            Log.d("stdout","colour byte is: " + colourByte.toInt())
            // we also cannot send all 0's since its the null terminator
            // so we will set the second highest bit
            res.add(colourInt)
        }
        return res
    }
}

class HardCodedColourPalette {
    data class RGBColor(val r: Int, val g: Int, val b: Int)

    // note: these need to match whats on the watch
    val colorPalette8: List<RGBColor> = listOf(
        RGBColor(255, 0, 0),     // Red
        RGBColor(0, 255, 0),     // Green
        RGBColor(0, 0, 255),     // Blue
        RGBColor(255, 255, 0),   // Yellow
        RGBColor(255, 0, 255),   // Magenta
        RGBColor(0, 255, 255),   // Cyan
        RGBColor(255, 255, 255),   // White
        RGBColor(0, 0, 0)         // Black
    )


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
        return paletteIndex.toByte()
    }

    fun convertColourToPalette(colour: Colour): Byte {
        val packedColor = rgbTo6Bit(colour.red.toInt(), colour.green.toInt(), colour.blue.toInt(), colorPalette8)
        println("Packed color (6-bit): 0x${String.format("%02X", packedColor)}")
        return packedColor
    }
}


