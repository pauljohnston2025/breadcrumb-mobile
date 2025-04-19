package com.paul.infrastructure.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.ceil

class ImageProcessor(private val context: Context) {

    suspend fun parseImage(uri: Uri, res: Int): Bitmap? {
        return parseImage(context.contentResolver.openInputStream(uri)!!, res)
    }

    suspend fun parseImage(stream: InputStream, res: Int): Bitmap? {
        var imageData: ByteArray = byteArrayOf()
        withContext(Dispatchers.IO) {
            try {
                imageData = InputStreamHelpers.readAllBytes(stream)
                stream.close()
            }
            catch(t: Throwable)
            {
                Napier.d("stream load failed $t")
                return@withContext null
            }
        }
        return parseImage(imageData, res)
    }

    /**
     * Reads image data from a byte array, decodes it, and resizes it.
     *
     * @param res The Output image size in pixels `res x res`
     * @param imageData The byte array containing the image data.  Can be JPEG, PNG, etc.
     * @return A Bitmap object representing the resized image, or null if there was an error.
     */
    suspend fun parseImage(imageData: ByteArray, res: Int): Bitmap? {
        try {
            // Decode the byte array into a Bitmap
            val options = BitmapFactory.Options()

            //Decodes a bitmap from the specified byte array
            val imageStream = ByteArrayInputStream(imageData)
            val bitmap = BitmapFactory.decodeStream(imageStream, null, options)

            if (bitmap == null) {
                return null // Handle decoding failure
            }

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, res, res, true)

            // Recycle the original bitmap to free up memory (important for large images)
            // createScaledBitmap can copy the bitmap if there is no scaling,
            // so do not recycle the original bitmap
            if (resizedBitmap !== bitmap)
            {
                bitmap.recycle()
            }
            withContext(Dispatchers.IO) {
                imageStream.close()
            }
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
}