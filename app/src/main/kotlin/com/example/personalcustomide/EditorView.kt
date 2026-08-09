package com.example.personalcustomide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import kotlin.math.min

class EditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var native: EditorNative? = null

    // Paints for token colors
    private val defaultPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 40f
        color = 0xFF000000.toInt()
    }
    private val keywordPaint = TextPaint(defaultPaint).apply { color = 0xFF0000FF.toInt() }
    private val stringPaint = TextPaint(defaultPaint).apply { color = 0xFF008000.toInt() }
    private val commentPaint = TextPaint(defaultPaint).apply { color = 0xFF808080.toInt() }
    private val numberPaint = TextPaint(defaultPaint).apply { color = 0xFFFF8C00.toInt() }
    private val operatorPaint = TextPaint(defaultPaint).apply { color = 0xFF800080.toInt() }
    private val preprocessorPaint = TextPaint(defaultPaint).apply { color = 0xFF808000.toInt() }
    private val identifierPaint = TextPaint(defaultPaint).apply { color = 0xFF000000.toInt() }

    private val tokenPaintMap = mapOf(
        LexerTokenType.KEYWORD to keywordPaint,
        LexerTokenType.STRING to stringPaint,
        LexerTokenType.COMMENT to commentPaint,
        LexerTokenType.NUMBER to numberPaint,
        LexerTokenType.OPERATOR to operatorPaint,
        LexerTokenType.PREPROCESSOR to preprocessorPaint,
        LexerTokenType.IDENTIFIER to identifierPaint,
        LexerTokenType.DEFAULT to defaultPaint
    )

    private var lineHeight = 0f
    private var charWidth = 0f

    private var cursorPos = 0
    private var cursorVisible = true
    private var cursorBlinkRunnable: Runnable? = null

    private var textLines: List<String> = listOf()
    private var tokenCache: MutableMap<Int, List<TokenInfo>> = mutableMapOf()
    private var visibleLinesRange = IntRange(0, 0)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        lineHeight = defaultPaint.fontSpacing
        charWidth = defaultPaint.measureText("W")
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val lineIndex = ((event.y - 10) / lineHeight).toInt().coerceIn(0, textLines.size - 1)
                var charIndex = 0
                // Rough estimate based on x position
                charIndex = ((event.x - 10) / charWidth).toInt().coerceIn(0, textLines[lineIndex].length)
                var pos = 0
                for (i in 0 until lineIndex) pos += textLines[i].length + 1
                pos += charIndex
                setCursorPos(pos)
                return@setOnTouchListener true
            }
            false
        }

        startCursorBlink()
    }

    fun setEditorNative(native: EditorNative) {
        this.native = native
        updateText()
    }

    private fun updateText() {
        native?.let {
            val fullText = it.getText()
            textLines = fullText.split("\n", keepEmpty = true)
            // Invalidate cache and re-tokenize visible lines later in onDraw
            tokenCache.clear()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (textLines.isEmpty()) {
            canvas.drawText("[empty file]", 10f, 10f + lineHeight, defaultPaint)
            return
        }

        // Determine visible lines based on canvas height
        val visibleStart = 0
        val visibleEnd = min(textLines.size, ((height - 20) / lineHeight).toInt() + 2)
        val visibleRange = visibleStart until visibleEnd

        // Tokenize lines that are now visible and not cached
        val linesToTokenize = visibleRange.filter { it !in tokenCache.keys }
        if (linesToTokenize.isNotEmpty()) {
            val startTime = System.currentTimeMillis()
            native?.let { n ->
                for (line in linesToTokenize) {
                    tokenCache[line] = n.getLineTokens(line)
                }
            }
            // Log if tokenization took too long
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 50) {
                Log.d("EditorView", "Tokenization took $elapsed ms for ${linesToTokenize.size} lines")
            }
        }

        var yPos = 10f + lineHeight
        for (lineIndex in visibleStart until visibleEnd) {
            val line = textLines[lineIndex]
            val tokens = tokenCache[lineIndex] ?: listOf()
            var xPos = 10f
            var tokenStart = 0
            for (token in tokens) {
                val paint = tokenPaintMap[token.type] ?: defaultPaint
                val tokenText = line.substring(tokenStart, min(tokenStart + token.length, line.length))
                canvas.drawText(tokenText, xPos, yPos, paint)
                xPos += paint.measureText(tokenText)
                tokenStart += token.length
            }
            // Draw cursor
            if (cursorVisible && hasFocus()) {
                val absCursor = cursorPos
                var lineStart = 0
                for (i in 0 until lineIndex) {
                    lineStart += textLines[i].length + 1
                }
                val lineEnd = lineStart + line.length
                if (absCursor in lineStart..lineEnd) {
                    val offsetInLine = absCursor - lineStart
                    val beforeText = line.substring(0, offsetInLine)
                    val cursorX = 10f + defaultPaint.measureText(beforeText)
                    canvas.drawLine(cursorX, yPos - lineHeight, cursorX, yPos, defaultPaint)
                }
            }
            yPos += lineHeight
        }

        // Clean up tokens outside visible range to save memory
        val keysToRemove = tokenCache.keys.filter { it !in visibleRange }
        keysToRemove.forEach { tokenCache.remove(it) }
    }

    // Keyboard handling
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (cursorPos > 0) {
                    native?.deleteText(cursorPos - 1, 1)
                    cursorPos--
                    updateText()
                }
                return true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (cursorPos < (native?.getText()?.length ?: 0)) {
                    native?.deleteText(cursorPos, 1)
                    updateText()
                }
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                native?.insertText(cursorPos, "\n")
                cursorPos++
                updateText()
                return true
            }
            else -> {
                val unicode = event.unicodeChar
                if (unicode != 0 && event.action == KeyEvent.ACTION_DOWN) {
                    if (Character.isValidCodePoint(unicode)) {
                        val char = Char(unicode)
                        native?.insertText(cursorPos, char.toString())
                        cursorPos++
                        updateText()
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        return BaseInputConnection(this, true) {
            val text = it.toString()
            if (text.isNotEmpty()) {
                native?.insertText(cursorPos, text)
                cursorPos += text.length
                updateText()
            }
            true
        }
    }

    fun setCursorPos(pos: Int) {
        cursorPos = pos.coerceIn(0, native?.getText()?.length ?: 0)
        invalidate()
    }

    private fun startCursorBlink() {
        cursorBlinkRunnable?.let { removeCallbacks(it) }
        cursorBlinkRunnable = Runnable {
            cursorVisible = !cursorVisible
            invalidate()
            postDelayed(cursorBlinkRunnable, 500)
        }
        postDelayed(cursorBlinkRunnable, 500)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            cursorVisible = false
            invalidate()
        } else {
            cursorVisible = true
            startCursorBlink()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cursorBlinkRunnable?.let { removeCallbacks(it) }
        // Release native reference (optional)
        native = null
    }

    enum class LexerTokenType {
        KEYWORD, IDENTIFIER, NUMBER, STRING, COMMENT, OPERATOR, PREPROCESSOR, DEFAULT
    }

    data class TokenInfo(val type: LexerTokenType, val length: Int)
}