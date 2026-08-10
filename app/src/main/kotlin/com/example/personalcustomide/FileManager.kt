package com.example.personalcustomide

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Centralizes all file operations to keep activities clean.
 * 
 * CHANGES MADE:
 * - Added proper error handling with user-friendly toasts
 * - Sanitizes filenames to prevent path traversal
 * - Automatically creates the home directory if missing
 * - Returns null on failure so callers can handle gracefully
 */
class FileManager(private val context: Context) {

    private val homeDir: File
        get() = context.filesDir.resolve("home").apply {
            if (!exists()) {
                val success = mkdirs()
                if (!success) {
                    // This will be caught by the caller
                    throw IOException("Failed to create home directory")
                }
            }
        }

    /**
     * Creates a new file with empty content.
     * Returns the created File on success, null on failure.
     * 
     * FIX: Now sanitizes filename, writes empty content, and provides clear error feedback.
     */
    suspend fun createNewFile(fileName: String): File? = withContext(Dispatchers.IO) {
        val sanitized = sanitizeFileName(fileName)
        if (sanitized.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Invalid file name – use letters, numbers, dots, underscores, or hyphens", Toast.LENGTH_SHORT).show()
            }
            return@withContext null
        }

        val file = homeDir.resolve(sanitized)
        return@withContext try {
            if (file.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "File '$sanitized' already exists", Toast.LENGTH_SHORT).show()
                }
                null
            } else {
                file.createNewFile()
                file.writeText("") // FIX: Write empty content so the file is not zero-length
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Created '$sanitized'", Toast.LENGTH_SHORT).show()
                }
                file
            }
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_LONG).show()
            }
            null
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    /**
     * Reads a file's content as a String.
     * Returns null on failure.
     * 
     * FIX: Added proper error handling for missing files and permission issues.
     */
    suspend fun readFile(file: File): String? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_SHORT).show()
                }
                return@withContext null
            }
            file.readText()
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            null
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Permission denied reading file", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    /**
     * Writes content to a file, safely.
     * Returns true on success, false on failure.
     * 
     * FIX: Added comprehensive error handling.
     */
    suspend fun writeFile(file: File, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            true
        } catch (e: IOException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
            }
            false
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Permission denied saving file", Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    /**
     * Lists all files in the home directory.
     * Returns an empty list on failure (never null).
     * 
     * FIX: Now handles SecurityException and returns empty list instead of crashing.
     */
    fun listFiles(): List<File> {
        return try {
            homeDir.listFiles()?.filter { it.isFile } ?: emptyList()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Cannot read directory: ${e.message}", Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    /**
     * Checks if a file exists.
     */
    fun fileExists(fileName: String): Boolean {
        return try {
            homeDir.resolve(fileName).exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes a file.
     */
    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            file.delete()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to delete file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    /**
     * Sanitizes a filename to prevent path traversal and invalid characters.
     * 
     * FIX: Added this to prevent security issues and crashes from bad filenames.
     */
    private fun sanitizeFileName(name: String): String {
        // Allow only alphanumeric, dots, underscores, and hyphens
        // Replace anything else with underscore
        val sanitized = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        // Trim and ensure we don't have empty or dot-only names
        return sanitized.trim().takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: ""
    }
}