package com.paul.protocol.todevice

class CompanionAppTileServerChanged(private val tilLayerMin: Int, private val tilLayerMax: Int) :
    Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_COMPANION_APP_TILE_SERVER_CHANGED
    }

    override fun payload(): List<Any> {
        return mutableListOf(tilLayerMin, tilLayerMax)
    }
}