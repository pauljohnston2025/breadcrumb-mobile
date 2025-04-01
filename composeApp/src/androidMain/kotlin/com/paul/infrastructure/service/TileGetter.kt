package com.paul.infrastructure.service

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import com.paul.protocol.todevice.Colour
import com.paul.protocol.todevice.MapTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

// todo: move this into common code
// only thing holding it back is the image processing and web requests
class TileGetter(
    private val imageProcessor: ImageProcessor,
    private val fileHelper: IFileHelper,
) : ITileGetter {
    override suspend fun getTile(req: LoadTileRequest): LoadTileResponse
    {
        val bigTileSize = 256f
        val smallTilesPerBigTile = Math.ceil(bigTileSize.toDouble()/req.tileSize).toInt()
        val scaleUpSize = smallTilesPerBigTile * req.tileSize
        val x = req.x / smallTilesPerBigTile
        val y = req.y / smallTilesPerBigTile

        // todo cache tiles and do this way better (get tiles based on pixels)
//        val brisbaneUrl = "https://a.tile.opentopomap.org/11/1894/1186.png"
//        var tileUrl = brisbaneUrl;
        val tileUrl = "https://a.tile.opentopomap.org/${req.z}/${x}/${y}.png"
        Log.d("stdout", "fetching $tileUrl")

        val fileName = "tiles/${req.z}/${x}/${y}.png"

        val bitmaps: List<Bitmap>
        try {
            var tileContents = fileHelper.readFile(fileName)
            if (tileContents == null)
            {
                val address = URL(tileUrl)
                val connection: HttpURLConnection
                withContext(Dispatchers.IO) {
                    connection = address.openConnection(Proxy.NO_PROXY) as HttpURLConnection
                    connection.connect()
                }
                if (connection.responseCode != 200) {
                    Log.d("stdout", "fetching $tileUrl failed ${connection.responseCode}")
                    return erroredTile(req)
                }

                withContext(Dispatchers.IO) {
                    tileContents = connection.inputStream.readAllBytes()
                }
                fileHelper.writeLocalFile(fileName, tileContents!!)
            }

            val resizedBitmap = imageProcessor.parseImage(tileContents, scaleUpSize)!!
            bitmaps = imageProcessor.splitBitmapDynamic(resizedBitmap, req.tileSize, req.tileSize)!!
        }
        catch (e: Throwable)
        {
            Log.d("stdout", "fetching $tileUrl failed $e")
            return erroredTile(req)
        }

        val xOffset = req.x % smallTilesPerBigTile.toInt()
        val yOffset = req.y % smallTilesPerBigTile.toInt()
        val offset = xOffset * smallTilesPerBigTile.toInt() + yOffset
        if (offset >= bitmaps.size || offset < 0)
        {
            Log.d("stdout", "our math aint mathing $offset")
            return erroredTile(req)
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

        val tile = MapTile(req.x, req.y, req.z, colourData)

        return LoadTileResponse(tile.colourString())
    }
}