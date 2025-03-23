package com.paul.infrastructure.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil

class ImageProcessor(private val context: Context) {
    private var getContentLauncher: ActivityResultLauncher<Array<String>>? =
        null
    private var searchCompletion: ((Uri?) -> Unit)? = null

    fun setLauncher(_getContentLauncher: ActivityResultLauncher<Array<String>>) {
        require(getContentLauncher == null)
        getContentLauncher = _getContentLauncher
    }

    /**
     * Reads image data from a byte array, decodes it, and resizes it.
     *
     * @param res The Output image size in pixels `res x res`
     * @param imageData The byte array containing the image data.  Can be JPEG, PNG, etc.
     * @return A Bitmap object representing the resized image, or null if there was an error.
     */
    suspend fun parseImage(uri: Uri, res: Int): Bitmap? {
        var imageData: ByteArray
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)!!
            imageData = stream.readAllBytes()
            stream.close()
        }

        try {
            // Decode the byte array into a Bitmap
            val options = BitmapFactory.Options()

            //Decodes a bitmap from the specified byte array
            val imageStream = ByteArrayInputStream(imageData)
            var bitmap = BitmapFactory.decodeStream(imageStream, null, options)

            if (bitmap == null) {
                return null // Handle decoding failure
            }

            // Resize the Bitmap to 100x100
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, res, res, true)

            // Recycle the original bitmap to free up memory (important for large images)
            bitmap.recycle()
            imageStream.close()
            return resizedBitmap
        } catch (e: Exception) {
            e.printStackTrace() // Log the exception for debugging
            return null // Handle any exceptions during the process
        }
    }

    /**
     * Splits a Bitmap into smaller Bitmap objects of specified dimensions.
     *
     * @param originalBitmap The Bitmap to split.
     * @param chunkWidth The desired width of each chunk.
     * @param chunkHeight The desired height of each chunk.
     * @return A list containing the smaller Bitmap objects, or null if there was an error.
     */
    fun splitBitmapDynamic(
        originalBitmap: Bitmap,
        chunkWidth: Int,
        chunkHeight: Int
    ): List<Bitmap>? {

        if (chunkWidth <= 0 || chunkHeight <= 0) {
            return null // Handle null input or invalid chunk dimensions
        }

        val bitmaps = mutableListOf<Bitmap>()
        val originalWidth = originalBitmap.width
        val originalHeight = originalBitmap.height

        // Calculate the number of rows and columns
        val numColumns = ceil(originalWidth.toDouble() / chunkWidth).toInt()
        val numRows = ceil(originalHeight.toDouble() / chunkHeight).toInt()

        for (col in 0 until numColumns) {
            for (row in 0 until numRows) {
                val x = col * chunkWidth
                val y = row * chunkHeight

                // Adjust width and height to handle edge cases where the chunk might be smaller
                // than chunkWidth or chunkHeight because it's at the edge of the image.
                val width = minOf(chunkWidth, originalWidth - x)
                val height = minOf(chunkHeight, originalHeight - y)

                if(width > 0 && height > 0) {
                    val bitmap = Bitmap.createBitmap(originalBitmap, x, y, width, height)
                    bitmaps.add(bitmap)
                }
            }
        }

        return bitmaps
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun searchForImageFileUri(): Uri = suspendCancellableCoroutine { continuation ->
        require(searchCompletion == null)
        searchCompletion = { uri ->
            if (uri == null) {
                continuation.resumeWithException(Exception("failed to load file: $uri"))
            } else {
                Log.d("stdout","Load file: " + uri.toString())
                continuation.resume(uri) {
                    Log.d("stdout","failed to resume")
                }
            }

            searchCompletion = null
        }

        require(getContentLauncher != null)
        getContentLauncher!!.launch(arrayOf("*"))
    }

    public suspend fun writeUriToFile(filename: String, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri)!!
                val imageData = stream.readAllBytes()
                stream.close()

                val file = File(context.filesDir, filename)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(imageData)
                }
            } catch (e: IOException) {
                e.printStackTrace() // Log the exception for debugging
            }
        }
    }

    fun fileLoaded(uri: Uri?) {
        require(searchCompletion != null)
        searchCompletion!!(uri)
    }
}