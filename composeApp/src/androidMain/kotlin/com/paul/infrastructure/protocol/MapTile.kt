package com.paul.infrastructure.protocol

data class Colour(
    private val red: UByte,
    private val green: UByte,
    private val blue: UByte,
)
{
    fun asGarminInt(): Int {
        val colour =  (red.toUInt() shl 16) or (green.toUInt() shl 8) or blue.toUInt()
//        println("red is: " + red.toInt());
//        println("red is: " + red.toUInt());
//        println("colour is: " + colour);
        return colour.toInt();
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

        data.add(x);
        data.add(y);

        for (colour in pixelData) {
            data.add(colour.asGarminInt())
        }

        return data
    }
}