package com.example.personalcustomide

object EditorNative {
    init {
        System.loadLibrary("editor")
    }

    external fun loadFile(path: String): Boolean
    external fun getText(): String
    external fun insertText(pos: Int, text: String)
    external fun deleteText(pos: Int, len: Int)
    external fun saveFile(path: String): Boolean
    // For syntax highlighting, we'll return token colors later
}