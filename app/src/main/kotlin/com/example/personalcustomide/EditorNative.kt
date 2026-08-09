package com.example.personalcustomide

import android.util.Log
import com.example.personalcustomide.EditorView.LexerTokenType

object EditorNative {
    private const val TAG = "EditorNative"

    init {
        try {
            System.loadLibrary("editor")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    // Native methods – all return default values on failure
    external fun nativeLoadFile(path: String): Boolean
    external fun nativeGetText(): String
    external fun nativeInsertText(pos: Int, text: String): Boolean
    external fun nativeDeleteText(pos: Int, len: Int): Boolean
    external fun nativeSaveFile(path: String): Boolean
    external fun nativeGetLineTokens(line: Int): String  // encoded "type,length;type,length;..."

    // Safe public wrappers with try-catch
    fun loadFile(path: String): Boolean {
        return try {
            nativeLoadFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "loadFile failed", e)
            false
        }
    }

    fun getText(): String {
        return try {
            nativeGetText() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "getText failed", e)
            ""
        }
    }

    fun insertText(pos: Int, text: String): Boolean {
        return try {
            nativeInsertText(pos, text)
        } catch (e: Exception) {
            Log.e(TAG, "insertText failed", e)
            false
        }
    }

    fun deleteText(pos: Int, len: Int): Boolean {
        return try {
            nativeDeleteText(pos, len)
        } catch (e: Exception) {
            Log.e(TAG, "deleteText failed", e)
            false
        }
    }

    fun saveFile(path: String): Boolean {
        return try {
            nativeSaveFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "saveFile failed", e)
            false
        }
    }

    // Parse token string into list of TokenInfo
    fun getLineTokens(line: Int): List<EditorView.TokenInfo> {
        return try {
            val encoded = nativeGetLineTokens(line) ?: return emptyList()
            if (encoded.isEmpty()) return emptyList()
            encoded.split(";").mapNotNull { part ->
                val (typeStr, lenStr) = part.split(",")
                val type = LexerTokenType.values().getOrNull(typeStr.toIntOrNull() ?: -1)
                val len = lenStr.toIntOrNull() ?: 0
                if (type != null && len > 0) EditorView.TokenInfo(type, len) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getLineTokens failed for line $line", e)
            emptyList()
        }
    }
}