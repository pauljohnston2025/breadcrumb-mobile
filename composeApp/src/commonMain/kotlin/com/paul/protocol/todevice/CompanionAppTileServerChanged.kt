package com.paul.protocol.todevice

import com.paul.domain.ColourPalette

// There is a small bug documented below:
//BUT: If we send this every time the app starts, the watch app will need to change
//
//_storageTileCache.clearValues() is called from the watch app when we get PROTOCOL_COMPANION_APP_TILE_SERVER_CHANGED
//we do not want opening the companion app to clear our storge tiles
//
//
//The watch could start, and no webserver is running on the phone (so the watch gets set to tilelayerMax=8)
//This also happens when first picking companion app tile server on the watch, if the webserver is not running it defaults to 8, and prompts for the app to be opened
//but currently when the app opens the tile server options are not sent, when the tileserver starts (on app open) we could send the CompanionAppTileServerChanged info to the watch, just incase its out of date
//Its currently asserted when we change any of the tile server settings
//
//
//Another downside of sending CompanionAppTileServerChanged on startup is that the user will have to select a watch to send the settings too, meaning they cannot get past this if they open the app and dont have a watch connected
//it should be single shot once when the app opens and after the device list is populated and only result in an error message saying could not send tile server settings
//if the user only has one watch, it auto picks the one in the list, so most users should not notice anything strange
//
//There will still be a race though
//
//Watch app opens, gets stuck at tilelayerMax=8 because companion app selected and tile server not running
//user closes watch app in frustration of blury tiles, or to restart and try again
//on restart of the watch app it does not (currently) query the tile server max/min layers
//user then notices the prompt for tile server to be started, and launches app on phone (which hopefully send the new tile server info)
//user then opens the watch app again, and its till stuck as 8
//
//This will hopefuilly not be an issue, since the tile server details should be stored in the watch apps message queue, so if we constantly re-assert them it should be fine (as olong as it does not break the wtch app with OOM or something)
//
// Ultimately, we cannot send CompanionAppTileServerChanged all the time, or the watch will constantly be nuking storage tiles
// in the rare cases where the watch gets stuck on tilelayerMax=8, the user will just have to stop and start the tile server, and it will fix itself
// though that brings up another issue, if we change any tile server setting (even disable/enable) it will nuke the storage cache on the watch.

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