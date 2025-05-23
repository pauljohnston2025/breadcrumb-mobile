package com.paul.infrastructure.repositories

import ch.qos.logback.core.subst.Token
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.TileServerRepo.Companion.getAuthTokenOnStart
import com.paul.infrastructure.repositories.TileServerRepo.Companion.getTileServerOnStart
import com.paul.infrastructure.repositories.TileServerRepo.Companion.getTileTypeOnStart
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.web.KtorClient
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import com.paul.infrastructure.web.TileServerDetailsResponse
import com.paul.infrastructure.web.TileType
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

abstract class ITileRepository(private val fileHelper: IFileHelper) {
    private val client = KtorClient.client // Get the singleton client instance

    private var tileServer = getTileServerOnStart()
    private var authToken = getAuthTokenOnStart()
    protected var tileType = getTileTypeOnStart()

    fun currentTileServer(): TileServerInfo {
        return tileServer
    }

    fun serverDetails(): TileServerDetailsResponse {
        return TileServerDetailsResponse(tileServer.tileLayerMin, tileServer.tileLayerMax)
    }

    fun setTileServer(tileServer: TileServerInfo) {
        this.tileServer = tileServer
        // todo: nuke local tile cache?
        // maybe a tile cache per url?
    }

    fun setAuthToken(authToken: String) {
        this.authToken = authToken
    }

    fun updateTileType(tileType: TileType) {
        this.tileType = tileType
    }

    abstract suspend fun getWatchTile(req: LoadTileRequest): Pair<Int, LoadTileResponse?>

    // gets a full size tile
    suspend fun seedLayer(
        xMin: Int,
        xMax: Int,
        yMin: Int,
        yMax: Int,
        z: Int,
        progressCallback: suspend (Float) -> Unit,
        errorCallback: suspend (x: Int, y: Int, z: Int, Throwable) -> Unit
    ) {
        // Calculate total number of tiles expected - Use Long to avoid overflow
        val totalTiles = (xMax - xMin + 1L) * (yMax - yMin + 1L)
        if (totalTiles <= 0) {
            progressCallback(1.0f) // Nothing to do, report 100%
            return
        }

        val processedTiles = AtomicLong(0L) // Thread-safe counter for progress

        // Use coroutineScope to wait for all launched jobs to complete
        coroutineScope {
            for (x in xMin..xMax) {
                for (y in yMin..yMax) {
                    // Launch a new coroutine for each tile fetch.
                    // These will run concurrently, limited by the available threads
                    // in the dispatcher (likely Default or IO if getTile switches).
                    launch { // Uses the scope's default dispatcher unless specified
                        try {
                            getTile(x, y, z)
                        } catch (e: Exception) {
                            // Report the error for this specific tile
                            errorCallback(x, y, z, e)
                        } finally {

                            // Still count it as "processed" in terms of the loop attempt,
                            // otherwise progress might never reach 100% if errors occur.
                            // Alternatively, you could choose *not* to increment here
                            // if progress should only reflect successful fetches.
                            val currentProcessed = processedTiles.incrementAndGet()
                            progressCallback(
                                (currentProcessed.toFloat() / totalTiles.toFloat()).coerceIn(0f, 1f)
                            )
                            // Optional: Log the error locally as well
                            // Napier.d("Error fetching tile ($x, $y, $z): ${e.message}")
                        }
                    }
                }
            }
        }
    }

    suspend fun getTile(x: Int, y: Int, z: Int): Pair<Int, ByteArray?> {
        val tileUrl = tileServer.url
            .replace("{x}", "${x}")
            .replace("{y}", "${y}")
            .replace("{z}", "${z}")
            .replace("{authToken}", this.authToken)
//        Napier.d("Loading tile $tileUrl")

        if (z < tileServer.tileLayerMin || z > tileServer.tileLayerMax) {
            Napier.w("Tile url outsize z layer $tileUrl")
            return Pair(404, null)
        }
        //        val brisbaneUrl = "https://a.tile.opentopomap.org/11/1894/1186.png"
        //        var tileUrl = brisbaneUrl;

        val fileName = "tiles/${tileServer.id}/${z}/${x}/${y}.png"

        try {
            var tileContents = fileHelper.readLocalFile(fileName)
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
                    // todo: cache tile errors, and show them to user to (especially for map page)
                    return Pair(response.status.value, null)
                }

                tileContents = withContext(Dispatchers.IO) {
                    return@withContext response.bodyAsChannel().toByteArray()
                }
                fileHelper.writeLocalFile(fileName, tileContents)
            }

            return Pair(200, tileContents)
        } catch (e: Throwable) {
            Napier.d("fetching $tileUrl failed $e")
            return Pair(500, null)
        }
    }
}