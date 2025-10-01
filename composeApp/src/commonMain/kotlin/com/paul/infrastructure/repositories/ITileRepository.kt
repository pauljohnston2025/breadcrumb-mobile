package com.paul.infrastructure.repositories

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.paul.composables.byteArrayToImageBitmap
import com.paul.domain.ColourPalette
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.ColourPaletteRepository.Companion.getSelectedPaletteOnStart
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

class ITileRepository(private val fileHelper: IFileHelper) {
    private val client = KtorClient.client // Get the singleton client instance

    private var tileServer = getTileServerOnStart()
    protected var _currentPalette = getSelectedPaletteOnStart()
    private var authToken = getAuthTokenOnStart()
    protected var tileType = getTileTypeOnStart()

    fun currentTileServer(): TileServerInfo {
        return tileServer
    }

    fun currentPalette(): ColourPalette {
        return _currentPalette
    }

    fun serverDetails(): TileServerDetailsResponse {
        return TileServerDetailsResponse(tileServer.tileLayerMin, tileServer.tileLayerMax)
    }

    fun setTileServer(tileServer: TileServerInfo) {
        this.tileServer = tileServer
    }

    fun setCurrentPalette(currentPalette: ColourPalette) {
        this._currentPalette = currentPalette
    }

    fun setAuthToken(authToken: String) {
        this.authToken = authToken
    }

    fun updateTileType(tileType: TileType) {
        this.tileType = tileType
    }

    suspend fun getWatchTile(req: LoadTileRequest): Pair<Int, LoadTileResponse?> {
        val smallTilesPerScaledTile =
            Math.ceil(req.scaledTileSize.toDouble() / req.tileSize).toInt()
        val scaleUpSize = smallTilesPerScaledTile * req.tileSize
        val x = req.x / smallTilesPerScaledTile
        val y = req.y / smallTilesPerScaledTile
        Napier.d("webserver full tile req: $x, $y, ${req.z}")

        val tileContents = getTile(x, y, req.z)
        if (tileContents.first != 200 || tileContents.second == null) {
            return Pair(tileContents.first, null)
        }

        // 1. Decode byte array to a multiplatform ImageBitmap
        val sourceBitmap = try {
            byteArrayToImageBitmap(tileContents.second!!)
        } catch (e: Throwable) {
            Napier.d("failed to parse bitmap from bytes", e)
            return Pair(500, null)
        }

        if (sourceBitmap == null) {
            Napier.d("decoded bitmap is null")
            return Pair(500, null)
        }

        // 2. Resize the ImageBitmap by drawing it to a new, larger canvas
        val resizedBitmap = ImageBitmap(scaleUpSize, scaleUpSize)
        val canvas = Canvas(resizedBitmap)
        val paint = Paint()
        canvas.drawImageRect(
            image = sourceBitmap,
            srcSize = IntSize(sourceBitmap.width, sourceBitmap.height),
            dstSize = IntSize(scaleUpSize, scaleUpSize),
            paint = paint
        )

        // 3. Split the resized bitmap into a list of smaller tile bitmaps.
        // The loop is column-major to match the original offset calculation.
        val bitmaps = mutableListOf<ImageBitmap>()
        for (col in 0 until smallTilesPerScaledTile) {
            for (row in 0 until smallTilesPerScaledTile) {
                val tileBitmap = ImageBitmap(req.tileSize, req.tileSize)
                val tileCanvas = Canvas(tileBitmap)
                tileCanvas.drawImageRect(
                    image = resizedBitmap,
                    srcOffset = IntOffset(col * req.tileSize, row * req.tileSize),
                    srcSize = IntSize(req.tileSize, req.tileSize),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(req.tileSize, req.tileSize),
                    paint = paint
                )
                bitmaps.add(tileBitmap)
            }
        }

        val xOffset = req.x % smallTilesPerScaledTile
        val yOffset = req.y % smallTilesPerScaledTile
        val offset = xOffset * smallTilesPerScaledTile + yOffset

        if (offset >= bitmaps.size || offset < 0) {
            Napier.d("our math aint mathing. offset: $offset, size: ${bitmaps.size}")
            return Pair(500, null)
        }
        val targetBitmap = bitmaps[offset]

        // 4. Read pixel data from the target bitmap
        val pixelCount = req.tileSize * req.tileSize
        val pixelArray = IntArray(pixelCount)
        targetBitmap.readPixels(
            buffer = pixelArray,
            startX = 0,
            startY = 0,
            width = req.tileSize,
            height = req.tileSize
        )

        // 5. Extract colour data, RECONSTRUCTING the original COLUMN-MAJOR order.
        // This is the key fix. We iterate in column-major order (col, then row) and
        // calculate the correct index to pull from the row-major pixelArray.
        // yep, i know, the pixels on the watch are received in column major order
        // the companion app does
        // for (var i = 0; i < tileSize; ++i) {
        //    for (var j = 0; j < tileSize; ++j) {
        //       localDc.drawPoint(i, j);
        val columnMajorPixelArray = IntArray(pixelCount)
        val width = req.tileSize
        var destIndex = 0
        for (col in 0 until width) {
            for (row in 0 until req.tileSize) {
                val srcIndex = row * width + col
                columnMajorPixelArray[destIndex++] = pixelArray[srcIndex]
            }
        }

        val tile = MapTile(req.x, req.y, req.z, columnMajorPixelArray)

        val paletteId =
            if (tileType == TileType.TILE_DATA_TYPE_64_COLOUR) _currentPalette.watchAppPaletteId else null

        return Pair(
            200,
            LoadTileResponse(
                tileType.value.toInt(),
                tile.colourString(tileType, _currentPalette),
                paletteId
            )
        )
    }

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