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
        for (colour in pixelData) {
            val colourByte = colour.asCharColour()
//            Log.d("stdout","colour byte is: " + colourByte.toInt())
            // we also cannot send all 0's since its the null terminator
            // so we will set the second highest bit
            val byteVal = ((colourByte.toInt() or 0x40) and 0x7F).toByte()
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
    val colorPalette64: List<RGBColor> = listOf(
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
        val packedColor = rgbTo6Bit(colour.red.toInt(), colour.green.toInt(), colour.blue.toInt(), colorPalette64)
//        println("Packed color (6-bit): 0x${String.format("%02X", packedColor)}")
        return packedColor
    }
}


