package com.paul.infrastructure.service

interface IZipArchive : AutoCloseable {
    fun getEntry(name: String): IZipEntry?
    fun entries(): List<IZipEntry>
}

interface IZipEntry {
    val name: String
    val isDirectory: Boolean
    /** Reads the entire entry as bytes */
    fun readBytes(): ByteArray
}
