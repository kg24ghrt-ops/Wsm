package com.example.personalcustomide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileWriter

object AutoSaveManager {
    private const val TAG = "AutoSaveManager"
    private const val DRAFT_DIR = "drafts"
    private const val DRAFT_EXTENSION = ".draft"

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var isRunning = false
    private var currentFilePath: String? = null
    private var currentContent: String? = null
    private var autoSaveEnabled = true
    private var autoSaveInterval = 30000L

    fun initialize(context: Context) {
        getDraftDir(context).mkdirs()
    }

    fun start(context: Context, filePath: String, content: String, interval: Long = 30000) {
        stop()
        currentFilePath = filePath
        currentContent = content
        autoSaveInterval = interval
        autoSaveEnabled = true
        isRunning = true

        handler = Handler(Looper.getMainLooper())

        // FIX: Store the Runnable in a variable first, then use it inside the lambda
        val newRunnable = Runnable {
            if (isRunning && autoSaveEnabled) {
                saveDraft(context, filePath, content)
                if (autoSaveEnabled) {
                    saveToFile(filePath, content)
                }
                // FIX: Use the stored variable reference, not 'this'
                handler?.postDelayed(newRunnable, autoSaveInterval)
            }
        }
        runnable = newRunnable
        handler?.postDelayed(newRunnable, autoSaveInterval)
    }

    fun updateContent(content: String) {
        currentContent = content
    }

    fun stop() {
        isRunning = false
        runnable?.let { handler?.removeCallbacks(it) }
        handler = null
        runnable = null
    }

    fun saveDraft(context: Context, filePath: String, content: String) {
        try {
            val draftFile = getDraftFile(context, filePath)
            FileWriter(draftFile).use { it.write(content) }
            Log.d(TAG, "Draft saved for: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save draft", e)
        }
    }

    fun saveToFile(filePath: String, content: String): Boolean {
        return try {
            File(filePath).writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-save file", e)
            false
        }
    }

    fun getDraft(context: Context, filePath: String): String? {
        return try {
            val draftFile = getDraftFile(context, filePath)
            if (draftFile.exists()) draftFile.readText() else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read draft", e)
            null
        }
    }

    fun deleteDraft(context: Context, filePath: String) {
        try {
            getDraftFile(context, filePath).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete draft", e)
        }
    }

    fun hasDraft(context: Context, filePath: String): Boolean {
        return getDraftFile(context, filePath).exists()
    }

    fun getDraftFile(context: Context, filePath: String): File {
        val safeName = filePath.replace(File.separatorChar, '_')
        return File(getDraftDir(context), "$safeName$DRAFT_EXTENSION")
    }

    private fun getDraftDir(context: Context): File {
        return File(context.filesDir, DRAFT_DIR)
    }

    fun isAutoSaveEnabled(): Boolean = autoSaveEnabled
    fun setAutoSaveEnabled(enabled: Boolean) { autoSaveEnabled = enabled }
    fun getAutoSaveInterval(): Long = autoSaveInterval
    fun setAutoSaveInterval(interval: Long) { autoSaveInterval = interval }
}