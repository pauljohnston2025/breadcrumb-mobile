package com.paul.infrastructure.repositories

import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.ImageProcessor
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import com.paul.protocol.todevice.Colour
import com.paul.protocol.todevice.MapTile
import io.github.aakira.napier.Napier

// todo: move this into common code
// only thing holding it back is the image processing (which can be done like the map tiles)
class TileRepository(
    private val imageProcessor: ImageProcessor,
    fileHelper: IFileHelper,
) : ITileRepository(fileHelper) {

    override suspend fun getWatchTile(req: LoadTileRequest): LoadTileResponse {
//        Napier.d("small tile req: $req")
        val smallTilesPerScaledTile = Math.ceil(req.scaledTileSize.toDouble() / req.tileSize).toInt()
        val scaleUpSize = smallTilesPerScaledTile * req.tileSize
        val x = req.x / smallTilesPerScaledTile
        val y = req.y / smallTilesPerScaledTile
        Napier.d("webserver full tile req: $x, $y, ${req.z}")

        val tileContents = getTile(x, y, req.z)
        if (tileContents == null) {
            return erroredTile(req)
        }
        val bitmaps = try {
            val resizedBitmap = imageProcessor.parseImage(tileContents, scaleUpSize)!!
            imageProcessor.splitBitmapDynamic(resizedBitmap, req.tileSize, req.tileSize)!!
        } catch (e: Throwable) {
            Napier.d("failed to parse bitmap")
            return erroredTile(req)
        }

        val xOffset = req.x % smallTilesPerScaledTile
        val yOffset = req.y % smallTilesPerScaledTile
        val offset = xOffset * smallTilesPerScaledTile + yOffset
        if (offset >= bitmaps.size || offset < 0) {
            Napier.d("our math aint mathing $offset")
            return erroredTile(req)
        }
        val bitmap = bitmaps[offset]
        val colourData = mutableListOf<Colour>()
        for (pixelX in 0 until req.tileSize) {
            for (pixelY in 0 until req.tileSize) {
                val colour = bitmap.getPixel(pixelX, pixelY)
                colourData.add(
                    Colour(
                        colour.red.toUByte(), colour.green.toUByte(), colour.blue.toUByte()
                    )
                )
            }
        }

        val tile = MapTile(req.x, req.y, req.z, colourData)

        return LoadTileResponse(tileType.value.toInt(), tile.colourString(tileType))
    }
}
