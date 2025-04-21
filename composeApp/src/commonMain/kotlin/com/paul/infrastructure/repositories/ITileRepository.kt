package com.paul.infrastructure.repositories

import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.TileServerRepo.Companion.getTileServerOnStart
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.web.KtorClient
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import com.paul.infrastructure.web.platformInfo
import com.paul.protocol.todevice.Colour
import com.paul.protocol.todevice.MapTile
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class ITileRepository(private val fileHelper: IFileHelper,) {
    private val client = KtorClient.client // Get the singleton client instance

    private var tileServer = getTileServerOnStart()

    fun setTileServer(tileServer: TileServerInfo) {
        this.tileServer = tileServer
        // todo: nuke local tile cache?
        // maybe a tile cache per url?
    }

    fun erroredTile(req: LoadTileRequest): LoadTileResponse {
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

    abstract suspend fun getWatchTile(req: LoadTileRequest): LoadTileResponse
    // gets a full size tile
    suspend fun getTile(x: Int, y: Int, z: Int): ByteArray? {
        //        val brisbaneUrl = "https://a.tile.opentopomap.org/11/1894/1186.png"
        //        var tileUrl = brisbaneUrl;
        val tileUrl = tileServer.url
            .replace("{x}", "${x}")
            .replace("{y}", "${y}")
            .replace("{z}", "${z}")
        Napier.d("Loading tile $tileUrl")

        val fileName = "tiles/${tileServer.id}/${z}/${x}/${y}.png"

        try {
            var tileContents = fileHelper.readFile(fileName)
            if (tileContents == null) {
                val response = withContext(Dispatchers.IO) {
                    return@withContext client.get(tileUrl) {
                        // required by openstreetmaps, not sure how to get this to work
                        // https://operations.osmfoundation.org/policies/tiles/
                        // https://help.openstreetmap.org/questions/29938/in-my-app-problem-downloading-maptile-000-http-response-http11-403-forbidden
                        header("User-Agent", "Breadcrumb/1.0 ${platformInfo()}")
                    }
                }
                if (!response.status.isSuccess()) {
                    Napier.d("fetching $tileUrl failed ${response.status}")
                    return null
                }

                tileContents = withContext(Dispatchers.IO) {
                    return@withContext response.bodyAsChannel().toByteArray()
                }
                fileHelper.writeLocalFile(fileName, tileContents)
            }

            return tileContents
        } catch (e: Throwable) {
            Napier.d("fetching $tileUrl failed $e")
            return null
        }
    }
}