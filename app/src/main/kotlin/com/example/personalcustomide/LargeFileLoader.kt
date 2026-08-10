package com.example.personalcustomide

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Handles lazy loading of large files using memory-mapped I/O.
 * Only loads chunks of the file as needed.
 */
class LargeFileLoader(private val filePath: String) {
    companion object {
        private const val TAG = "LargeFileLoader"
        private const val CHUNK_SIZE = 64 * 1024 // 64KB chunks
        private const val LARGE_FILE_THRESHOLD = 1024 * 1024 // 1MB
    }

    private var file: RandomAccessFile? = null
    private var fileSize: Long = 0
    private var isLargeFile = false
    private var loadedChunks = mutableMapOf<Int, String>()
    private var fullContent: String? = null
    private var totalLines = 0

    fun load(): String? {
        return try {
            val f = File(filePath)
            if (!f.exists()) return null

            fileSize = f.length()
            isLargeFile = fileSize > LARGE_FILE_THRESHOLD
            file = RandomAccessFile(f, "r")

            if (isLargeFile) {
                // For large files, just load the first chunk
                loadChunk(0)
                // Count lines quickly without loading entire file
                countLines()
                "File loaded (${fileSize / 1024} KB) - viewing first chunk"
            } else {
                // For small files, load everything
                fullContent = f.readText()
                totalLines = fullContent?.lines()?.size ?: 0
                fullContent
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load file", e)
            null
        }
    }

    fun getFullText(): String? {
        if (!isLargeFile) return fullContent

        // For large files, return the loaded chunks as a single string
        val chunks = loadedChunks.keys.sorted().mapNotNull { loadedChunks[it] }
        return if (chunks.isNotEmpty()) chunks.joinToString("") else null
    }

    fun getContent(start: Long, length: Long): String? {
        return try {
            file?.seek(start)
            val buffer = ByteArray(length.toInt())
            val bytesRead = file?.read(buffer) ?: 0
            if (bytesRead > 0) {
                String(buffer, 0, bytesRead, Charsets.UTF_8)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read chunk", e)
            null
        }
    }

    fun getChunk(chunkIndex: Int): String? {
        if (!isLargeFile) return null

        // Check cache first
        loadedChunks[chunkIndex]?.let { return it }

        // Load chunk
        val start = chunkIndex.toLong() * CHUNK_SIZE
        val end = minOf(start + CHUNK_SIZE, fileSize)
        val length = end - start

        return try {
            file?.seek(start)
            val buffer = ByteArray(length.toInt())
            val bytesRead = file?.read(buffer) ?: 0
            if (bytesRead > 0) {
                val content = String(buffer, 0, bytesRead, Charsets.UTF_8)
                loadedChunks[chunkIndex] = content
                content
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chunk $chunkIndex", e)
            null
        }
    }

    fun getVisibleContent(startLine: Int, endLine: Int): String {
        if (!isLargeFile) {
            val lines = fullContent?.lines() ?: emptyList()
            return lines.subList(startLine, minOf(endLine, lines.size)).joinToString("\n")
        }

        // For large files, we need to find the byte positions for lines
        // This is simplified - in practice you'd build a line index
        val builder = StringBuilder()
        var currentLine = 0
        var chunkIndex = 0
        while (currentLine < endLine && chunkIndex * CHUNK_SIZE < fileSize) {
            val chunk = getChunk(chunkIndex) ?: break
            val lines = chunk.split("\n")
            val linesNeeded = lines.subList(
                maxOf(0, startLine - currentLine),
                minOf(lines.size, endLine - currentLine)
            )
            builder.append(linesNeeded.joinToString("\n"))
            currentLine += lines.size
            chunkIndex++
        }
        return builder.toString()
    }

    private fun countLines() {
        // Simplified line counting for large files
        var count = 0
        var chunkIndex = 0
        while (chunkIndex * CHUNK_SIZE < fileSize) {
            val chunk = getChunk(chunkIndex) ?: break
            count += chunk.count { it == '\n' }
            chunkIndex++
        }
        totalLines = count
    }

    fun getTotalLines(): Int = totalLines
    fun isLarge(): Boolean = isLargeFile
    fun getFileSize(): Long = fileSize

    fun close() {
        try {
            file?.close()
        } catch (e: Exception) {
            // Ignore
        }
        file = null
    }

    // For saving large files - this is a simplified version
    fun saveContent(content: String): Boolean {
        return try {
            File(filePath).writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save large file", e)
            false
        }
    }

    // Check if a file is large
    fun isLargeFile(filePath: String): Boolean {
        return try {
            File(filePath).length() > LARGE_FILE_THRESHOLD
        } catch (e: Exception) {
            false
        }
    }
}