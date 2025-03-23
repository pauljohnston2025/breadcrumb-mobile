package com.paul.infrastructure.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.paul.infrastructure.protocol.Colour
import com.paul.infrastructure.protocol.MapTile
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

class TileGetter(
    private val imageProcessor: ImageProcessor,
    private val context: Context,
) {
    suspend fun getTile(req: LoadTileRequest): LoadTileResponse
    {
        val bigTileSize = 256f
        val smallTilesPerBigTile = bigTileSize/req.tileSize
        val x = (req.x / smallTilesPerBigTile).toInt();
        val y = (req.y / smallTilesPerBigTile).toInt();

        // todo cache tiles and do this way better (get tiles based on pixels)
        val tileUrl = "https://a.tile.opentopomap.org/${req.z}/${x}/${y}.png"
        Log.d("stdout", "fetching $tileUrl")
        val bitmaps: List<Bitmap>
        try {
            val address = URL(tileUrl)
            val connection: HttpURLConnection
            withContext(Dispatchers.IO) {
                connection = address.openConnection(Proxy.NO_PROXY) as HttpURLConnection
                connection.connect()
            }
            if (connection.responseCode != 200) {
                Log.d("stdout", "fetching $tileUrl failed ${connection.responseCode}")
                val colourData = List(req.tileSize * req.tileSize) {
                    Colour(
                        0.toUByte(),
                        0.toUByte(),
                        0.toUByte()
                    )
                }
                val tile = MapTile(req.x, req.y, colourData)
                return LoadTileResponse(tile.colourString())
            }


            val resizedBitmap = imageProcessor.parseImage(connection.inputStream, bigTileSize.toInt())!!
            bitmaps = imageProcessor.splitBitmapDynamic(resizedBitmap, req.tileSize, req.tileSize)!!
        }
        catch (e: Throwable)
        {
            Log.d("stdout", "fetching $tileUrl failed $e")
            val colourData = List(req.tileSize * req.tileSize) {
                Colour(
                    0.toUByte(),
                    0.toUByte(),
                    0.toUByte()
                )
            }
            val tile = MapTile(req.x, req.y, colourData)
            return LoadTileResponse(tile.colourString())
        }

        val xOffset = req.x % smallTilesPerBigTile.toInt()
        val yOffset = req.y % smallTilesPerBigTile.toInt()
        val offset = xOffset * smallTilesPerBigTile.toInt() + yOffset
        if (offset > bitmaps.size || offset< 0)
        {
            Log.d("stdout", "our math aint mathing $offset")
            val colourData = List(req.tileSize * req.tileSize) {
                Colour(
                    0.toUByte(),
                    0.toUByte(),
                    0.toUByte()
                )
            }
            val tile = MapTile(req.x, req.y, colourData)
            return LoadTileResponse(tile.colourString())
        }
        val bitmap = bitmaps[offset]
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

        val tile = MapTile(req.x, req.y, colourData)

        return LoadTileResponse(tile.colourString())
    }
}