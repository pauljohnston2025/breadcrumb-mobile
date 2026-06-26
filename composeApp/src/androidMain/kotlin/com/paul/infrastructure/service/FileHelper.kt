package com.paul.infrastructure.service

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater
import kotlin.coroutines.resumeWithException

class FileHelper(
    private val context: Context
) : IFileHelper {

    private var getContentLauncher: ActivityResultLauncher<Array<String>>? = null
    private var searchCompletion: ((Uri?) -> Unit)? = null

    fun registerLauncher(activity: ComponentActivity) {
        getContentLauncher = activity.activityResultRegistry.register(
            "file_picker_key",
            activity,
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            fileLoaded(uri)
        }
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

    override suspend fun writeFile(uriString: String, lines: List<String>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.bufferedWriter().use { writer ->
                        lines.forEach { line ->
                            writer.write(line)
                            writer.newLine()
                        }
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun findFile(mimeTypes: List<String>?): String = suspendCancellableCoroutine { continuation ->
        val launcher = getContentLauncher ?: throw IllegalStateException(
            "findFile() can only be called from an instance of FileHelper registered with an Activity."
        )

        require(searchCompletion == null)
        searchCompletion = { uri ->
            if (uri == null) {
                continuation.resumeWithException(Exception("failed to load file: $uri"))
            } else {
                Napier.d("Load file: " + uri.toString())
                continuation.resume(uri.toString()) { _, _, _ -> }
            }
            searchCompletion = null
        }

        val actualMimeTypes = mimeTypes?.toTypedArray() ?: arrayOf(
            "application/gpx+xml", "application/xml", "text/xml", "text/plain",
            "application/octet-stream", "application/fit", "application/vnd.ant.fit"
        )
        launcher.launch(actualMimeTypes)
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
        val completion = searchCompletion
        if (completion != null) {
            completion(uri)
        } else {
            Napier.w("fileLoaded called but searchCompletion is null. This can happen after a configuration change. URI: $uri", tag = "FileHelper")
        }
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
        val f = if (file.startsWith("/")) File(file) else File(context.filesDir, file)
        f.delete()
    }

    override suspend fun openZip(uri: String): IZipArchive? {
        return withContext(Dispatchers.IO) {
            try {
                val parsedUri = Uri.parse(uri)
                if (parsedUri.scheme == "content") {
                    val pfd = context.contentResolver.openFileDescriptor(parsedUri, "r") ?: return@withContext null
                    try {
                        val fdPath = "/proc/self/fd/${pfd.fd}"
                        AndroidZipArchive(java.util.zip.ZipFile(fdPath), pfd)
                    } catch (e: Exception) {
                        Napier.w("ZipFile failed with EACCES, falling back to manual FileChannel parser for: $uri")
                        FileChannelZipArchive(pfd)
                    }
                } else {
                    val file = if (uri.startsWith("/")) File(uri) else File(context.filesDir, uri)
                    AndroidZipArchive(java.util.zip.ZipFile(file))
                }
            } catch (e: Exception) {
                Napier.e("Failed to open zip: $uri", e)
                null
            }
        }
    }

    override fun decompressGzip(data: ByteArray): ByteArray {
        return java.util.zip.GZIPInputStream(data.inputStream()).use { it.readBytes() }
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

class AndroidZipArchive(
    private val zipFile: java.util.zip.ZipFile,
    private val pfd: ParcelFileDescriptor? = null
) : IZipArchive {
    override fun getEntry(name: String): IZipEntry? = zipFile.getEntry(name)?.let { AndroidZipEntry(zipFile, it) }
    override fun entries(): List<IZipEntry> = zipFile.entries().asSequence().map { AndroidZipEntry(zipFile, it) }.toList()
    override fun close() {
        try { zipFile.close() } finally { pfd?.close() }
    }
}

class AndroidZipEntry(private val zipFile: java.util.zip.ZipFile, private val entry: java.util.zip.ZipEntry) : IZipEntry {
    override val name: String get() = entry.name
    override val isDirectory: Boolean get() = entry.isDirectory
    override fun readBytes(): ByteArray = zipFile.getInputStream(entry).use { it.readBytes() }
}

class FileChannelZipArchive(private val pfd: ParcelFileDescriptor) : IZipArchive {
    private val fis = FileInputStream(pfd.fileDescriptor)
    private val channel: FileChannel = fis.channel
    private val entries = mutableMapOf<String, ZipEntryInfo>()

    data class ZipEntryInfo(
        val name: String, val offset: Long, val compressedSize: Long,
        val uncompressedSize: Long, val compressionMethod: Int, val isDirectory: Boolean
    )

    init { parseCentralDirectory() }

    private fun parseCentralDirectory() {
        val size = channel.size()
        if (size < 22) return
        val searchSize = Math.min(size, 65536L + 22L)
        val buffer = ByteBuffer.allocate(searchSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        channel.readFully(buffer, size - searchSize)
        var eocdPos = -1
        for (i in (searchSize - 22).toInt() downTo 0) {
            if (buffer.getInt(i) == 0x06054b50) { eocdPos = i; break }
        }
        if (eocdPos == -1) { Napier.w("Manual ZIP: EOCD not found"); return }

        var cdSize = buffer.getInt(eocdPos + 12).toLong() and 0xFFFFFFFFL
        var cdOffset = buffer.getInt(eocdPos + 16).toLong() and 0xFFFFFFFFL

        if (cdOffset == 0xFFFFFFFFL || cdSize == 0xFFFFFFFFL) {
            val locatorPos = (size - searchSize) + eocdPos - 20
            val locatorBuf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            channel.readFully(locatorBuf, locatorPos)
            if (locatorBuf.getInt(0) == 0x07064b50) {
                val zip64EocdOffset = locatorBuf.getLong(8)
                val zip64EocdBuf = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
                channel.readFully(zip64EocdBuf, zip64EocdOffset)
                if (zip64EocdBuf.getInt(0) == 0x06064b50) {
                    cdSize = zip64EocdBuf.getLong(40)
                    cdOffset = zip64EocdBuf.getLong(48)
                }
            }
        }

        val cdBuffer = ByteBuffer.allocate(cdSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        channel.readFully(cdBuffer, cdOffset)
        while (cdBuffer.hasRemaining()) {
            if (cdBuffer.getInt() != 0x02014b50) break
            cdBuffer.position(cdBuffer.position() + 6)
            val method = cdBuffer.getShort().toInt() and 0xFFFF
            cdBuffer.position(cdBuffer.position() + 8)
            var compSize = cdBuffer.getInt().toLong() and 0xFFFFFFFFL
            var uncompSize = cdBuffer.getInt().toLong() and 0xFFFFFFFFL
            val nameLen = cdBuffer.getShort().toInt() and 0xFFFF
            val extraLen = cdBuffer.getShort().toInt() and 0xFFFF
            val commentLen = cdBuffer.getShort().toInt() and 0xFFFF
            cdBuffer.position(cdBuffer.position() + 8)
            var localOffset = cdBuffer.getInt().toLong() and 0xFFFFFFFFL
            val nameBytes = ByteArray(nameLen); cdBuffer.get(nameBytes); val name = String(nameBytes)

            val extraEnd = cdBuffer.position() + extraLen
            if (compSize == 0xFFFFFFFFL || uncompSize == 0xFFFFFFFFL || localOffset == 0xFFFFFFFFL) {
                while (cdBuffer.position() + 4 <= extraEnd) {
                    val tag = cdBuffer.getShort().toInt() and 0xFFFF
                    val sizeE = cdBuffer.getShort().toInt() and 0xFFFF
                    val blockEnd = cdBuffer.position() + sizeE
                    if (tag == 0x0001) {
                        if (uncompSize == 0xFFFFFFFFL && cdBuffer.position() + 8 <= blockEnd) uncompSize = cdBuffer.getLong()
                        if (compSize == 0xFFFFFFFFL && cdBuffer.position() + 8 <= blockEnd) compSize = cdBuffer.getLong()
                        if (localOffset == 0xFFFFFFFFL && cdBuffer.position() + 8 <= blockEnd) localOffset = cdBuffer.getLong()
                        break
                    } else cdBuffer.position(blockEnd)
                }
            }
            cdBuffer.position(extraEnd)
            cdBuffer.position(cdBuffer.position() + commentLen)
            entries[name] = ZipEntryInfo(name, localOffset, compSize, uncompSize, method, name.endsWith("/"))
        }
    }

    override fun getEntry(name: String): IZipEntry? = entries[name]?.let { FileChannelZipEntry(channel, it) }
    override fun entries(): List<IZipEntry> = entries.values.map { FileChannelZipEntry(channel, it) }
    override fun close() { try { fis.close() } finally { pfd.close() } }
}

class FileChannelZipEntry(private val channel: FileChannel, private val info: FileChannelZipArchive.ZipEntryInfo) : IZipEntry {
    override val name: String get() = info.name
    override val isDirectory: Boolean get() = info.isDirectory
    override fun readBytes(): ByteArray {
        var currentOffset = info.offset
        val headerBuf = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
        channel.readFully(headerBuf, currentOffset)

        var sig = headerBuf.getInt(0)
        if (sig != 0x04034b50) {
            Napier.w("Manual ZIP: Signature mismatch at $currentOffset for ${info.name}. Found 0x${Integer.toHexString(sig)}. Searching...")
            // Fallback: Some Zip64 implementations have slight offset misalignments. Search nearby.
            val searchBuf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            channel.readFully(searchBuf, currentOffset)
            var found = false
            for (i in 0 until 4092) {
                if (searchBuf.getInt(i) == 0x04034b50) {
                    currentOffset += i
                    channel.readFully(headerBuf, currentOffset)
                    sig = headerBuf.getInt(0)
                    found = true
                    Napier.d("Manual ZIP: Found local header for ${info.name} at adjusted offset $currentOffset")
                    break
                }
            }
            if (!found) throw IOException("Manual ZIP: Invalid local header for ${info.name} at ${info.offset} (searched nearby). Found: 0x${Integer.toHexString(sig)}")
        }

        val nameLen = headerBuf.getShort(26).toInt() and 0xFFFF
        val extraLen = headerBuf.getShort(28).toInt() and 0xFFFF
        val dataOffset = currentOffset + 30 + nameLen + extraLen

        val compressedData = ByteBuffer.allocate(info.compressedSize.toInt())
        channel.readFully(compressedData, dataOffset)
        val bytes = compressedData.array()

        return if (info.compressionMethod == 8) {
            val inflater = Inflater(true); inflater.setInput(bytes)
            val result = ByteArray(info.uncompressedSize.toInt())
            var offset = 0; while (!inflater.finished() && offset < result.size) {
                val inflated = inflater.inflate(result, offset, result.size - offset)
                if (inflated == 0) {
                    if (inflater.needsInput()) break
                }
                offset += inflated
            }; inflater.end(); result
        } else bytes
    }
}

fun FileChannel.readFully(dst: ByteBuffer, position: Long) {
    dst.clear()
    var currentPos = position
    while (dst.hasRemaining()) {
        val read = read(dst, currentPos)
        if (read == -1) break
        currentPos += read
    }
    dst.flip()
}
