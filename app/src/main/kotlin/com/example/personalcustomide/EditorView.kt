package com.example.personalcustomide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var native: EditorNative? = null
    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        color = 0xFF000000.toInt()
    }

    private var lineHeight = 0f
    private var charWidth = 0f

    init {
        // Measure line height and char width for layout
        paint.textSize = 40f
        lineHeight = paint.fontSpacing
        charWidth = paint.measureText("W") // approximate
    }

    fun setEditorNative(native: EditorNative) {
        this.native = native
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        native?.let { n ->
            val text = n.getText()
            if (text.isNotEmpty()) {
                val lines = text.split("\n")
                lines.forEachIndexed { index, line ->
                    canvas.drawText(line, 10f, 10f + (index + 1) * lineHeight, paint)
                }
            } else {
                canvas.drawText("[empty file]", 10f, 10f + lineHeight, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Simple cursor positioning - not implemented for brevity
        // In a real app, handle clicks to set cursor and show keyboard
        return super.onTouchEvent(event)
    }
}