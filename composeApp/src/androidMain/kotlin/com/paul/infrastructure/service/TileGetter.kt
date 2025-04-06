package com.paul.infrastructure.service

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.paul.infrastructure.web.KtorClient
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import com.paul.infrastructure.web.platformInfo
import com.paul.protocol.todevice.Colour
import com.paul.protocol.todevice.MapTile
import com.paul.viewmodels.Settings
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// todo: move this into common code
// only thing holding it back is the image processing
class TileGetter(
    private val imageProcessor: ImageProcessor,
    private val fileHelper: IFileHelper,
) : ITileGetter {

    private val client = KtorClient.client // Get the singleton client instance

    private var tileServer = Settings.getTileServerOnStart().url

    override fun setTileServer(tileServer: String)
    {
        this.tileServer = tileServer
        // todo: nuke local tile cache?
        // maybe a tile cache per url?
    }

    override suspend fun getTile(req: LoadTileRequest): LoadTileResponse
    {
        val bigTileSize = 256f
        val smallTilesPerBigTile = Math.ceil(bigTileSize.toDouble()/req.tileSize).toInt()
        val scaleUpSize = smallTilesPerBigTile * req.tileSize
        val x = req.x / smallTilesPerBigTile
        val y = req.y / smallTilesPerBigTile

//        val brisbaneUrl = "https://a.tile.opentopomap.org/11/1894/1186.png"
//        var tileUrl = brisbaneUrl;
        val tileUrl = tileServer
            .replace("{x}", "${x}")
            .replace("{y}", "${y}")
            .replace("{z}", "${req.z}")
        Log.d("stdout", "fetching $tileUrl")

        val fileName = "$tileServer/${req.z}/${x}/${y}.png"

        val bitmaps: List<Bitmap>
        try {
            var tileContents = fileHelper.readFile(fileName)
            if (tileContents == null)
            {
                val response = withContext(Dispatchers.IO) {
                    return@withContext client.get(tileUrl) {
                        // required by openstreetmaps, not sure how to get this to work
                        // https://operations.osmfoundation.org/policies/tiles/
                        // https://help.openstreetmap.org/questions/29938/in-my-app-problem-downloading-maptile-000-http-response-http11-403-forbidden
                        header("User-Agent", "Breadcrumb/1.0 ${platformInfo()}")
                    }
                }
                if (!response.status.isSuccess()) {
                    Log.d("stdout", "fetching $tileUrl failed ${response.status}")
                    return erroredTile(req)
                }

                tileContents = withContext(Dispatchers.IO) {
                    return@withContext response.bodyAsChannel().toByteArray()
                }
                fileHelper.writeLocalFile(fileName, tileContents)
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