package com.example.personalcustomide

import android.app.AlertDialog
import android.content.Context
import android.widget.*
import androidx.core.widget.doAfterTextChanged

class SettingsDialog(private val context: Context) {

    interface Callback {
        fun onSettingsChanged(settings: EditorSettings)
    }

    fun show(currentSettings: EditorSettings, callback: Callback) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        // Font Size
        layout.addView(TextView(context).apply {
            text = "Font Size: ${currentSettings.fontSize.toInt()}"
            textSize = 14f
        })
        val fontSizeSeekBar = SeekBar(context).apply {
            max = 60
            progress = (currentSettings.fontSize - 12).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val size = (progress + 12).toFloat()
                    (seekBar?.parent as? LinearLayout)?.findViewById<TextView>(0)?.text = "Font Size: ${size.toInt()}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(fontSizeSeekBar)

        // Theme
        layout.addView(TextView(context).apply {
            text = "Theme"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        })
        val themeSpinner = Spinner(context).apply {
            val themes = arrayOf("Light", "Dark")
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, themes)
            setSelection(if (currentSettings.theme == EditorSettings.Theme.DARK) 1 else 0)
        }
        layout.addView(themeSpinner)

        // Tab Size
        layout.addView(TextView(context).apply {
            text = "Tab Size: ${currentSettings.tabSize}"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        })
        val tabSizeSpinner = Spinner(context).apply {
            val sizes = arrayOf("2", "4", "8")
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, sizes)
            setSelection(sizes.indexOf(currentSettings.tabSize.toString()))
        }
        layout.addView(tabSizeSpinner)

        // Checkboxes
        val wordWrapCheck = CheckBox(context).apply {
            text = "Word Wrap"
            isChecked = currentSettings.wordWrap
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        layout.addView(wordWrapCheck)

        val autoSaveCheck = CheckBox(context).apply {
            text = "Auto-Save"
            isChecked = currentSettings.autoSave
        }
        layout.addView(autoSaveCheck)

        val lineNumbersCheck = CheckBox(context).apply {
            text = "Show Line Numbers"
            isChecked = currentSettings.showLineNumbers
        }
        layout.addView(lineNumbersCheck)

        val syntaxHighlightCheck = CheckBox(context).apply {
            text = "Syntax Highlighting"
            isChecked = currentSettings.enableSyntaxHighlighting
        }
        layout.addView(syntaxHighlightCheck)

        // Auto-save interval
        layout.addView(TextView(context).apply {
            text = "Auto-Save Interval (seconds): ${currentSettings.autoSaveInterval / 1000}"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        })
        val intervalSeekBar = SeekBar(context).apply {
            max = 60
            progress = (currentSettings.autoSaveInterval / 1000).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    (seekBar?.parent as? LinearLayout)?.findViewById<TextView>(0)?.text =
                        "Auto-Save Interval (seconds): $progress"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        layout.addView(intervalSeekBar)

        AlertDialog.Builder(context)
            .setTitle("Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newSettings = EditorSettings(
                    fontSize = (fontSizeSeekBar.progress + 12).toFloat(),
                    theme = if (themeSpinner.selectedItemPosition == 1) EditorSettings.Theme.DARK else EditorSettings.Theme.LIGHT,
                    tabSize = tabSizeSpinner.selectedItem.toString().toInt(),
                    wordWrap = wordWrapCheck.isChecked,
                    autoSave = autoSaveCheck.isChecked,
                    autoSaveInterval = intervalSeekBar.progress * 1000L,
                    showLineNumbers = lineNumbersCheck.isChecked,
                    enableSyntaxHighlighting = syntaxHighlightCheck.isChecked
                )
                callback.onSettingsChanged(newSettings)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}