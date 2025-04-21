package com.paul.infrastructure.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.aakira.napier.Napier
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resumeWithException

class FileHelper(
    private val context: Context,
) : IFileHelper {

    private var getContentLauncher: ActivityResultLauncher<Array<String>>? =
        null
    private var searchCompletion: ((Uri?) -> Unit)? = null

    fun setLauncher(_getContentLauncher: ActivityResultLauncher<Array<String>>) {
        require(getContentLauncher == null)
        getContentLauncher = _getContentLauncher
    }

    override suspend fun readFile(filename: String): ByteArray? {
        return readUri(Uri.parse(filename))
    }

    override suspend fun readLocalFile(filename: String): ByteArray? {
        return readUri(Uri.fromFile(File(context.filesDir, filename)))
    }

    private suspend fun readUri(uri: Uri): ByteArray? {
        return try {
            withContext(Dispatchers.IO) {
                val fileInputStream = context.contentResolver.openInputStream(uri)!!
                val res = InputStreamHelpers.readAllBytes(fileInputStream)
                fileInputStream.close()
                return@withContext res
            }
        } catch (e: IOException) {
            // e.printStackTrace() // normally file not found
            null
        }
    }

    override suspend fun writeLocalFile(filename: String, data: ByteArray) {
        val file = File(context.filesDir, filename)

        // Ensure the parent directory exists
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs() // Create the parent directory and any missing intermediate directories
        }
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { outputStream ->
                outputStream.write(data)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun findFile(): String = suspendCancellableCoroutine { continuation ->
        require(searchCompletion == null)
        searchCompletion = { uri ->
            if (uri == null) {
                continuation.resumeWithException(Exception("failed to load file: $uri"))
            } else {
                Napier.d("Load file: " + uri.toString())
                continuation.resume(uri.toString()) {
                    Napier.d("failed to resume")
                }
            }

            searchCompletion = null
        }

        require(getContentLauncher != null)
        getContentLauncher!!.launch(arrayOf("*"))
    }

    override suspend fun getFileName(fullName: String): String? {
        val uri = Uri.parse(fullName)
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }

        if (uri.scheme != "content") {
            return null // Not a content URI
        }

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use { // Ensures cursor is closed after use
            if (it.moveToFirst()) {
                val displayNameColumnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameColumnIndex != -1) {
                    return it.getString(displayNameColumnIndex)
                }
            }
        }
        return null
    }

    fun fileLoaded(uri: Uri?) {
        require(searchCompletion != null)
        searchCompletion!!(uri)
    }

    override suspend fun localDirectorySize(directory: String): Long {
        return getDirectorySize(File(context.filesDir, directory))
    }

    private fun getDirectorySize(directory: File): Long {
        var totalSize: Long = 0
        // Check if it's a valid, readable directory
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return 0L
        }

        // List contents, handle potential null (e.g., permission issues)
        val files = directory.listFiles()
        files?.forEach { file ->
            totalSize += if (file.isDirectory) {
                getDirectorySize(file) // Recursive call for subdirectories
            } else {
                if (file.exists() && file.canRead()) {
                    file.length() // Add file size
                } else {
                    0L // Skip unreadable/non-existent files
                }
            }
        }
        return totalSize
    }

    override suspend fun deleteDir(directory: String) {
        File(context.filesDir, directory).deleteRecursively()
    }

    override suspend fun delete(file: String) {
        File(context.filesDir, file).delete()
    }

    override suspend fun localContentsSize(directoryPath: String): Map<String, Long> =
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, directoryPath)

            // Basic validation
            if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
                Napier.e("Directory is invalid or not readable: $directoryPath")
                return@withContext mapOf() // Indicate error or invalid path
            }

            val result = mutableMapOf<String, Long>()
            val files = directory.listFiles()

            if (files == null) {
                Napier.e("Failed to list files, check permissions for: $directoryPath")
                return@withContext mapOf() // Could be a permission issue
            }

            files.forEach { file ->
                try {
                    val size = if (file.isDirectory) {
                        getDirectorySize(file)
                    } else {
                        if (file.exists() && file.canRead()) file.length() else 0L
                    }

                    result.put(file.name, size)
                } catch (e: Exception) {
                    Napier.e("Error processing file: ${file.path}", e)
                }
            }

            result
        }
}