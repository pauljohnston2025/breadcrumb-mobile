package com.paul.infrastructure.service

import java.util.UUID

interface IFileHelper {
    suspend fun readFile(filename: String): ByteArray?
    suspend fun readLocalFile(filename: String): ByteArray?
    suspend fun writeLocalFile(filename: String, data: ByteArray)
    suspend fun writeFile(uriString: String, lines: List<String>): Boolean
    suspend fun getFileName(fullName: String) : String?
    /** returns the file name to open */
    suspend fun findFile(mimeTypes: List<String>? = null): String

    /** The size of each item in the given directory, returned in bytes */
    suspend fun localContentsSize(directory: String): Map<String, Long>
    /** The size of a directory in bytes */
    suspend fun localDirectorySize(directory: String): Long

    suspend fun deleteDir(directory: String)
    suspend fun delete(file: String)

    suspend fun openZip(uri: String): IZipArchive?

    fun decompressGzip(data: ByteArray): ByteArray

    fun generateRandomFilename(fileExtension: String? = null): String {
        val uuid = UUID.randomUUID().toString()
        return if (fileExtension != null && fileExtension.isNotEmpty()) {
            "$uuid.$fileExtension"
        } else {
            uuid
        }
    }
}