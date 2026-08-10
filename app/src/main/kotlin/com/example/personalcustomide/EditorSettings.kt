package com.example.personalcustomide

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class EditorSettings(
    val fontSize: Float = 40f,
    val theme: Theme = Theme.LIGHT,
    val tabSize: Int = 4,
    val wordWrap: Boolean = false,
    val autoSave: Boolean = true,
    val autoSaveInterval: Long = 30000, // milliseconds
    val showLineNumbers: Boolean = true,
    val enableSyntaxHighlighting: Boolean = true
) {
    enum class Theme {
        LIGHT, DARK
    }

    companion object {
        private const val PREFS_NAME = "editor_prefs"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
        private const val KEY_TAB_SIZE = "tab_size"
        private const val KEY_WORD_WRAP = "word_wrap"
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_AUTO_SAVE_INTERVAL = "auto_save_interval"
        private const val KEY_SHOW_LINE_NUMBERS = "show_line_numbers"
        private const val KEY_SYNTAX_HIGHLIGHTING = "syntax_highlighting"

        fun load(context: Context): EditorSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return EditorSettings(
                fontSize = prefs.getFloat(KEY_FONT_SIZE, 40f),
                theme = if (prefs.getString(KEY_THEME, "LIGHT") == "DARK") Theme.DARK else Theme.LIGHT,
                tabSize = prefs.getInt(KEY_TAB_SIZE, 4),
                wordWrap = prefs.getBoolean(KEY_WORD_WRAP, false),
                autoSave = prefs.getBoolean(KEY_AUTO_SAVE, true),
                autoSaveInterval = prefs.getLong(KEY_AUTO_SAVE_INTERVAL, 30000),
                showLineNumbers = prefs.getBoolean(KEY_SHOW_LINE_NUMBERS, true),
                enableSyntaxHighlighting = prefs.getBoolean(KEY_SYNTAX_HIGHLIGHTING, true)
            )
        }

        fun save(context: Context, settings: EditorSettings) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putFloat(KEY_FONT_SIZE, settings.fontSize)
                putString(KEY_THEME, settings.theme.name)
                putInt(KEY_TAB_SIZE, settings.tabSize)
                putBoolean(KEY_WORD_WRAP, settings.wordWrap)
                putBoolean(KEY_AUTO_SAVE, settings.autoSave)
                putLong(KEY_AUTO_SAVE_INTERVAL, settings.autoSaveInterval)
                putBoolean(KEY_SHOW_LINE_NUMBERS, settings.showLineNumbers)
                putBoolean(KEY_SYNTAX_HIGHLIGHTING, settings.enableSyntaxHighlighting)
            }
        }
    }
}