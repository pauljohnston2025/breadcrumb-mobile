package com.paul.protocol.todevice

import com.paul.domain.ColourPalette

class CompanionAppTileServerChanged(
    private val tilLayerMin: Int,
    private val tilLayerMax: Int,
    private val colourPalette: ColourPalette?
) :
    Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_COMPANION_APP_TILE_SERVER_CHANGED
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(
            tilLayerMin,
            tilLayerMax,
        )

        if (colourPalette != null) {
            data.add(colourPalette.watchAppPaletteId)
            data.add(colourPalette.allColoursForApp().map { it.toMonkeyCColourInt() })
        }

        return data
    }
}