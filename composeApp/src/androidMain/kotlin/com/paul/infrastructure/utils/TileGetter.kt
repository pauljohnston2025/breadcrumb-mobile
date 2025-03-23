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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

class TileGetter(
    private val imageProcessor: ImageProcessor,
    private val context: Context,
) {
    suspend fun getTile(req: LoadTileRequest): LoadTileResponse
    {
        // todo cache tiles and do this way better (get tiles based on pixels)
        val brisbaneUrl = "https://a.tile.opentopomap.org/11/1894/1186.png"
        val address = URL(brisbaneUrl)
        val connection: HttpURLConnection
        withContext(Dispatchers.IO) {
            connection = address.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection.connect()
        }
        if (connection.responseCode != 200) {
            val colourData = List(req.tileX * req.tileY) {Colour(0.toUByte(),0.toUByte(),0.toUByte())}
            val tile = MapTile(req.tileX, req.tileY, colourData)
            return LoadTileResponse(tile.colourString())
        }

//        val file = File(context.filesDir, "testimage.png")
//        val resizedBitmap = imageProcessor.parseImage(file.toUri(), req.tileSize * req.tileCountXY)
        val resizedBitmap = imageProcessor.parseImage(connection.inputStream, req.tileSize * req.tileCountXY)
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