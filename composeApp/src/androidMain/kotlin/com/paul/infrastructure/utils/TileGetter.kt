package com.paul.infrastructure.utils

import android.content.Context
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.net.toUri
import com.paul.infrastructure.protocol.Colour
import com.paul.infrastructure.protocol.MapTile
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import java.io.File

class TileGetter(
    private val imageProcessor: ImageProcessor,
    private val context: Context,
) {
    suspend fun getTile(req: LoadTileRequest): LoadTileResponse
    {
        val file = File(context.filesDir, "testimage.png")
        val resizedBitmap = imageProcessor.parseImage(file.toUri(), req.tileSize * req.tileCountXY)
        val bitmaps = imageProcessor.splitBitmapDynamic(resizedBitmap!!, req.tileSize, req.tileSize)

        val bitmap = bitmaps!![req.tileX * req.tileCountXY + req.tileY]
        val colourData = mutableListOf<Colour>()
        for (pixelX in 0 until req.tileSize) {
            for (pixelY in 0 until req.tileSize) {
                val colour = bitmap.getPixel(pixelX, pixelY)
                colourData.add(
                    Colour(
                        colour.red.toUByte(),
                        colour.green.toUByte(),
                        colour.blue.toUByte()
                    )
                )
            }
        }

        val tile = MapTile(req.tileX, req.tileY, colourData)

        return LoadTileResponse(tile.colourString())
    }
}