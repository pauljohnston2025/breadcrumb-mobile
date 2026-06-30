package com.paul.infrastructure.repositories

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class TileMetadata(
    val statusCode: Int,
    val expiryMillis: Long,
    val retryAfterMillis: Long? = null
)

data class TileResult(
    val statusCode: Int,
    val bitmap: ImageBitmap?,
    val metadata: TileMetadata
)

class ITileRepository(private val fileHelper: IFileHelper) {

    private val ioSemaphore = Semaphore(30) // Limit total parallel IO (Network + Disk)

    companion object {
        private const val TAG = "ITileRepository"
    }

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
        Napier.v("getWatchTile: tileId=($x, $y, ${req.z}), smallTile=(${req.x}, ${req.y})", tag = TAG)

        val tileContents = getTile(x, y, req.z)
        if (tileContents.statusCode != 200 || tileContents.bitmap == null) {
            return Pair(tileContents.statusCode, null)
        }

        val sourceBitmap = tileContents.bitmap

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
            Napier.e("Tile splitting logic error: offset $offset out of bounds (size ${bitmaps.size})", tag = TAG)
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

    suspend fun getTile(x: Int, y: Int, z: Int): TileResult {
        return getTileInternal(x, y, z, allowFallback = tileServer.fallbackToUpscaled)
    }

    private suspend fun getTileInternal(x: Int, y: Int, z: Int, allowFallback: Boolean): TileResult {
        val tileUrl = tileServer.url
            .replace("{x}", "${x}")
            .replace("{y}", "${y}")
            .replace("{z}", "${z}")
            .replace("{authToken}", this.authToken)

        if (z < tileServer.tileLayerMin || z > tileServer.tileLayerMax) {
            return TileResult(404, null, TileMetadata(404, Long.MAX_VALUE))
        }

        val metaFileName = "tiles/${tileServer.id}/${z}/${x}/${y}.meta"
        val dataFileName = "tiles/${tileServer.id}/${z}/${x}/${y}.png"

        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val metaBytes = fileHelper.readLocalFile(metaFileName)
            var cachedMeta: TileMetadata? = null

            if (metaBytes != null) {
                cachedMeta = try {
                    Json.decodeFromString<TileMetadata>(metaBytes.decodeToString())
                } catch (_: Exception) {
                    null
                }
            }

            // Migration / Legacy support: if PNG exists but no meta, treat as 200 but expired
            if (cachedMeta == null) {
                val legacyPng = fileHelper.readLocalFile(dataFileName)
                if (legacyPng != null) {
                    cachedMeta = TileMetadata(200, 0)
                }
            }

            // 1. If we have a valid, non-expired cache, use it immediately
            if (cachedMeta != null && (cachedMeta.retryAfterMillis ?: 0L) < now && cachedMeta.expiryMillis > now) {
                if (cachedMeta.statusCode == 200) {
                    val data = fileHelper.readLocalFile(dataFileName)
                    if (data != null) {
                        val bitmap = com.paul.composables.byteArrayToImageBitmap(data) as ImageBitmap?
                        if (bitmap != null) return TileResult(200, bitmap, cachedMeta)
                    }
                } else if (cachedMeta.statusCode == 404) {
                    return TileResult(cachedMeta.statusCode, null, cachedMeta)
                }
            }

            // 2. Try to fetch from network
            val networkResult = fetchFromNetwork(tileUrl)

            if (networkResult.first == 200) {
                // Success, update cache and return bitmap
                saveToCache(metaFileName, dataFileName, networkResult.first, networkResult.second)
                val metadata = TileMetadata(200, Clock.System.now().toEpochMilliseconds() + 30L * 24 * 60 * 60 * 1000)
                val bitmap = com.paul.composables.byteArrayToImageBitmap(networkResult.second) as ImageBitmap?
                return TileResult(200, bitmap, metadata)
            } else if (networkResult.first == 404) {
                // Not found on server. Check for upscaling fallback.
                if (allowFallback && z > tileServer.tileLayerMin) {
                    val upscaled = getUpscaledTile(x, y, z)
                    if (upscaled != null) {
                        // We found a parent to upscale from.
                        // We store the UPSCALED tile to disk so we don't have to keep doing it.
                        // We mark it as 200 in metadata because it's "valid" imagery for the user.
                        val upscaledBytes = com.paul.composables.imageBitmapToByteArray(upscaled)
                        saveToCache(metaFileName, dataFileName, 200, upscaledBytes)
                        val metadata = TileMetadata(200, Clock.System.now().toEpochMilliseconds() + 30L * 24 * 60 * 60 * 1000)
                        return TileResult(200, upscaled, metadata)
                    }
                }
                
                // If no fallback or fallback failed, cache the 404
                saveToCache(metaFileName, dataFileName, 404, null)
                return TileResult(404, null, TileMetadata(404, Clock.System.now().toEpochMilliseconds() + 7L * 24 * 60 * 60 * 1000))
            } else {
                // Network failed (500, timeout, offline, etc.)
                // 3. Fallback to expired cache if we have it
                if (cachedMeta != null) {
                    if (cachedMeta.statusCode == 200) {
                        val data = fileHelper.readLocalFile(dataFileName)
                        if (data != null) {
                            val bitmap = com.paul.composables.byteArrayToImageBitmap(data) as ImageBitmap?
                            if (bitmap != null) {
                                Napier.d("Using expired tile from cache due to fetch failure: $tileUrl")
                                return TileResult(200, bitmap, cachedMeta)
                            }
                        }
                    } else {
                        return TileResult(cachedMeta.statusCode, null, cachedMeta)
                    }
                }
                
                val errorMetadata = TileMetadata(networkResult.first, Clock.System.now().toEpochMilliseconds() + 10 * 60 * 1000, networkResult.third)
                saveToCache(metaFileName, dataFileName, networkResult.first, null, networkResult.third)
                return TileResult(networkResult.first, null, errorMetadata)
            }
        } catch (e: Throwable) {
            Napier.e("getTile unexpected error: exception=${e.message}, url=$tileUrl", e, tag = TAG)
            return TileResult(500, null, TileMetadata(500, Clock.System.now().toEpochMilliseconds() + 60 * 1000))
        }
    }

    private suspend fun getUpscaledTile(x: Int, y: Int, z: Int): ImageBitmap? {
        val parentX = x / 2
        val parentY = y / 2
        val parentZ = z - 1
        
        // We only want REAL tiles for the parent to avoid infinite low-quality recursion 
        // though one level of upscaling might be okay. Let's allow fallback for parent too
        // but limit how far we go by just calling getTileInternal which already handles zoom range.
        val parentResult = getTileInternal(parentX, parentY, parentZ, allowFallback = true)
        val parentBitmap = parentResult.bitmap ?: return null
        
        val targetSize = 256
        val upscaledBitmap = ImageBitmap(targetSize, targetSize)
        val canvas = Canvas(upscaledBitmap)
        val paint = Paint()
        
        val srcX = (x % 2) * 128
        val srcY = (y % 2) * 128
        
        canvas.drawImageRect(
            image = parentBitmap,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(128, 128),
            dstSize = IntSize(targetSize, targetSize),
            paint = paint
        )
        
        return upscaledBitmap
    }

    private suspend fun fetchFromNetwork(url: String): Triple<Int, ByteArray?, Long?> {
        return ioSemaphore.withPermit {
            try {
                val response = withContext(Dispatchers.IO) {
                    client.get(url) {
                        header("User-Agent", "Breadcrumb/1.0 ${platformInfo()}")
                    }
                }
                
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull()?.let {
                    Clock.System.now().toEpochMilliseconds() + (it * 1000)
                }

                if (response.status.isSuccess()) {
                    val bytes = response.bodyAsChannel().toByteArray()
                    Triple(200, bytes, null)
                } else {
                    Triple(response.status.value, null, retryAfter)
                }
            } catch (e: ClientRequestException) {
                val retryAfter = e.response.headers["Retry-After"]?.toLongOrNull()?.let {
                    Clock.System.now().toEpochMilliseconds() + (it * 1000)
                }
                if (e.response.status == HttpStatusCode.NotFound) {
                    Triple(404, null, null)
                } else {
                    Triple(e.response.status.value, null, retryAfter)
                }
            } catch (_: Throwable) {
                Triple(500, null, null)
            }
        }
    }


    private suspend fun saveToCache(metaFile: String, dataFile: String, statusCode: Int, data: ByteArray?, retryAfter: Long? = null) {
        val ttlMillis = if (statusCode == 200) {
            30L * 24 * 60 * 60 * 1000 // 30 days for successful tiles
        } else if (statusCode == 404) {
            7L * 24 * 60 * 60 * 1000 // 7 days for 404
        } else {
            10L * 60 * 1000 // 10 minutes for other errors (e.g. 500, 429)
        }
        val expiry = Clock.System.now().toEpochMilliseconds() + ttlMillis
        val meta = TileMetadata(statusCode, expiry, retryAfter)

        // Ensure cache is written even if the fetch job is cancelled.
        // By making this a suspend function, it respects the Semaphore permit from the caller.
        withContext(NonCancellable) {
            try {
                coroutineScope {
                    if (data != null) {
                        launch { fileHelper.writeLocalFile(dataFile, data) }
                    }
                    launch { fileHelper.writeLocalFile(metaFile, Json.encodeToString(meta).encodeToByteArray()) }
                }
            } catch (e: Exception) {
                Napier.e("Failed to write tile to cache: $metaFile", e, tag = TAG)
            }
        }
    }
}
