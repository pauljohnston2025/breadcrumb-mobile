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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
            var tileContents = readTile(fileName)
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
                    val colourData = List(req.tileSize * req.tileSize) {
                        Colour(
                            0.toUByte(),
                            0.toUByte(),
                            0.toUByte()
                        )
                    }
                    val tile = MapTile(req.x, req.y, req.z, colourData)
                    return LoadTileResponse(tile.colourString())
                }

                withContext(Dispatchers.IO) {
                    tileContents = connection.inputStream.readAllBytes()
                }
                writeToFile(fileName, tileContents!!)
            }

            val resizedBitmap = imageProcessor.parseImage(tileContents, scaleUpSize)!!
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
            val tile = MapTile(req.x, req.y, req.z, colourData)
            return LoadTileResponse(tile.colourString())
        }

        val xOffset = req.x % smallTilesPerBigTile.toInt()
        val yOffset = req.y % smallTilesPerBigTile.toInt()
        val offset = xOffset * smallTilesPerBigTile.toInt() + yOffset
        if (offset >= bitmaps.size || offset < 0)
        {
            Log.d("stdout", "our math aint mathing $offset")
            val colourData = List(req.tileSize * req.tileSize) {
                Colour(
                    0.toUByte(),
                    0.toUByte(),
                    0.toUByte()
                )
            }
            val tile = MapTile(req.x, req.y, req.z, colourData)
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

        val tile = MapTile(req.x, req.y, req.z, colourData)

        return LoadTileResponse(tile.colourString())
    }

    private fun writeToFile(filename: String, data: ByteArray) {
        val file = File(context.filesDir, filename)

        // Ensure the parent directory exists
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs() // Create the parent directory and any missing intermediate directories
        }

        FileOutputStream(file).use { outputStream ->
            outputStream.write(data)
        }
    }

    private fun readTile(filename: String): ByteArray? {
        val file = File(context.filesDir, filename)
        if (!file.exists()) {
            return null
        }

        return try {
            val fileInputStream = FileInputStream(file)
            val res = fileInputStream.readAllBytes()
            fileInputStream.close()
            return res
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}