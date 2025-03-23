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
    private val red: UByte,
    private val green: UByte,
    private val blue: UByte,
)
{
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
        data.add(colourList())
        return data
    }

    fun colourList(): List<Int> {
        // monkey c is a really annoying encoding, it does not allow sending a raw byte array
        // you can send strings, but they have to be valid utf8, fortunately we are not using the
        // top 2 bits in our encoding yet (so all our values are in the ascii range)
        // if we want to use the top 2 bits, we might have to send it as a whole heap of ints,
        // and deal with the overhead another way
        // (base64 encoding suggested, but that has its own overhead of ~33-37%)
        // extra byte per 4 byte sent is only 25% overhead
        // not using the top 2 bits has a 25% overhead too, but with 1 less byte for 4 colours sent
        // this is why we are switching to fetch the tiles from the watch rather than through pushing from app
        // http request seems much faster than connectIQ.sendMessage()

        var res = mutableListOf<Int>();
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