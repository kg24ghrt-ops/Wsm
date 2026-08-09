package com.example.personalcustomide

import com.example.personalcustomide.EditorView.LexerTokenType

object EditorNative {
    init {
        System.loadLibrary("editor")
    }

    external fun loadFile(path: String): Boolean
    external fun getText(): String
    external fun insertText(pos: Int, text: String)
    external fun deleteText(pos: Int, len: Int)
    external fun saveFile(path: String): Boolean

    // Returns a list of TokenInfo for the given line (0-indexed)
    external fun getLineTokens(line: Int): List<EditorView.TokenInfo>
}