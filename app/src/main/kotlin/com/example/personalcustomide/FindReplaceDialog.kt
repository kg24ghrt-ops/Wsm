package com.example.personalcustomide

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Dialog for find and replace functionality.
 */
class FindReplaceDialog(private val context: Context) {

    interface Callback {
        fun onFindNext(searchTerm: String, caseSensitive: Boolean)
        fun onFindPrevious(searchTerm: String, caseSensitive: Boolean)
        fun onReplaceAll(searchTerm: String, replaceTerm: String, caseSensitive: Boolean)
        fun onReplaceNext(searchTerm: String, replaceTerm: String, caseSensitive: Boolean)
        fun onClose()
    }

    private var callback: Callback? = null
    private var dialog: AlertDialog? = null

    fun show(callback: Callback) {
        this.callback = callback

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        // Find label and input
        layout.addView(TextView(context).apply {
            text = "Find:"
            textSize = 14f
        })
        val findInput = EditText(context).apply {
            hint = "Search term"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(findInput)

        // Replace label and input
        layout.addView(TextView(context).apply {
            text = "Replace with:"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        })
        val replaceInput = EditText(context).apply {
            hint = "Replace with"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(replaceInput)

        // Case sensitive checkbox
        val caseSensitiveCheckbox = android.widget.CheckBox(context).apply {
            text = "Case sensitive"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
        layout.addView(caseSensitiveCheckbox)

        // Button row
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        val buttonStyle = android.widget.LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { marginStart = 4; marginEnd = 4 }

        buttonRow.addView(Button(context).apply {
            text = "Find Next"
            layoutParams = buttonStyle
            setOnClickListener {
                val search = findInput.text.toString()
                if (search.isNotEmpty()) {
                    this@FindReplaceDialog.callback?.onFindNext(
                        search,
                        caseSensitiveCheckbox.isChecked
                    )
                } else {
                    Toast.makeText(context, "Enter search term", Toast.LENGTH_SHORT).show()
                }
            }
        })

        buttonRow.addView(Button(context).apply {
            text = "Find Prev"
            layoutParams = buttonStyle
            setOnClickListener {
                val search = findInput.text.toString()
                if (search.isNotEmpty()) {
                    this@FindReplaceDialog.callback?.onFindPrevious(
                        search,
                        caseSensitiveCheckbox.isChecked
                    )
                } else {
                    Toast.makeText(context, "Enter search term", Toast.LENGTH_SHORT).show()
                }
            }
        })

        buttonRow.addView(Button(context).apply {
            text = "Replace"
            layoutParams = buttonStyle
            setOnClickListener {
                val search = findInput.text.toString()
                val replace = replaceInput.text.toString()
                if (search.isNotEmpty()) {
                    this@FindReplaceDialog.callback?.onReplaceNext(
                        search,
                        replace,
                        caseSensitiveCheckbox.isChecked
                    )
                } else {
                    Toast.makeText(context, "Enter search term", Toast.LENGTH_SHORT).show()
                }
            }
        })

        buttonRow.addView(Button(context).apply {
            text = "Replace All"
            layoutParams = buttonStyle
            setOnClickListener {
                val search = findInput.text.toString()
                val replace = replaceInput.text.toString()
                if (search.isNotEmpty()) {
                    this@FindReplaceDialog.callback?.onReplaceAll(
                        search,
                        replace,
                        caseSensitiveCheckbox.isChecked
                    )
                } else {
                    Toast.makeText(context, "Enter search term", Toast.LENGTH_SHORT).show()
                }
            }
        })

        layout.addView(buttonRow)

        // Close button
        val closeButton = Button(context).apply {
            text = "Close"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            setOnClickListener {
                this@FindReplaceDialog.callback?.onClose()
                dialog?.dismiss()
            }
        }
        layout.addView(closeButton)

        dialog = AlertDialog.Builder(context)
            .setTitle("Find & Replace")
            .setView(layout)
            .setCancelable(true)
            .setOnCancelListener {
                callback.onClose()
            }
            .create()

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}