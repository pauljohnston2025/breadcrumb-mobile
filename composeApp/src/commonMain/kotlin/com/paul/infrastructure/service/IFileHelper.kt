package com.paul.infrastructure.service

import java.util.UUID

interface IFileHelper {
    suspend fun readFile(filename: String): ByteArray?
    suspend fun readLocalFile(filename: String): ByteArray?
    suspend fun writeLocalFile(filename: String, data: ByteArray)
    suspend fun getFileName(fullName: String) : String?
    /** returns the file name to open */
    suspend fun findFile(): String

    fun generateRandomFilename(fileExtension: String? = null): String {
        val uuid = UUID.randomUUID().toString()
        return if (fileExtension != null && fileExtension.isNotEmpty()) {
            "$uuid.$fileExtension"
        } else {
            uuid
        }
    }
}