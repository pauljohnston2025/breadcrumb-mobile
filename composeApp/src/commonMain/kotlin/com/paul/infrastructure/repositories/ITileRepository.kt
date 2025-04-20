package com.paul.infrastructure.repositories

import com.paul.domain.TileServerInfo
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import com.paul.protocol.todevice.Colour
import com.paul.protocol.todevice.MapTile

interface ITileRepository {
    suspend fun erroredTile(req: LoadTileRequest): LoadTileResponse
    {
        val colourData = List(req.tileSize * req.tileSize) {
            Colour(
                255.toUByte(), // red tiles for error
                0.toUByte(),
                0.toUByte()
            )
        }
        val tile = MapTile(req.x, req.y, req.z, colourData)
        return LoadTileResponse(tile.colourString())
    }

    suspend fun getTile(req: LoadTileRequest): LoadTileResponse
    fun setTileServer(tileServer: TileServerInfo)
}