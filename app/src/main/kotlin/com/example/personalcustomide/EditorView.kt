package com.example.personalcustomide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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

    // Editor paints
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

    // Line number paint
    private val lineNumberPaint = Paint().apply {
        isAntiAlias = true
        textSize = 32f
        color = Color.GRAY
    }

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

    // Line number configuration
    private var showLineNumbers: Boolean = true
    private val lineNumberMargin = 60f
    private val textStartX: Float get() = if (showLineNumbers) lineNumberMargin + 8f else 10f

    // Undo/Redo manager
    private val undoManager = EditorUndoManager()
    private var isUndoRedoInProgress = false

    // Find state
    private var findResults: List<Int> = emptyList()
    private var currentFindIndex = -1
    private var findHighlightPaint = Paint().apply {
        color = 0x44FFEB3B.toInt() // Semi-transparent yellow
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()

        lineHeight = defaultPaint.fontSpacing
        charWidth = defaultPaint.measureText("W")

        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val lineIndex = ((event.y - 10) / lineHeight).toInt().coerceIn(0, textLines.size - 1)
                var charIndex = ((event.x - textStartX) / charWidth).toInt().coerceIn(0, textLines[lineIndex].length)
                var pos = 0
                for (i in 0 until lineIndex) pos += textLines[i].length + 1
                pos += charIndex
                setCursorPos(pos)
                return@setOnTouchListener true
            }
            false
        }

        startCursorBlink()

        // Setup undo/redo callbacks
        undoManager.onStateChanged = { canUndo, canRedo ->
            onUndoRedoStateChanged?.invoke(canUndo, canRedo)
        }
    }

    fun setEditorNative(native: EditorNative) {
        this.native = native
        updateText()
        undoManager.clear()
    }

    fun setShowLineNumbers(show: Boolean) {
        showLineNumbers = show
        invalidate()
    }

    private fun updateText() {
        native?.let {
            val fullText = it.getText()
            textLines = fullText.split("\n".toRegex())
            tokenCache.clear()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (textLines.isEmpty()) {
            canvas.drawText("[empty file]", textStartX, 10f + lineHeight, defaultPaint)
            return
        }

        val visibleStart = 0
        val visibleEnd = min(textLines.size, ((height - 20) / lineHeight).toInt() + 2)
        val visibleRange = visibleStart until visibleEnd

        val linesToTokenize = visibleRange.filter { it !in tokenCache.keys }
        if (linesToTokenize.isNotEmpty()) {
            val startTime = System.currentTimeMillis()
            native?.let { n ->
                for (line in linesToTokenize) {
                    tokenCache[line] = n.getLineTokens(line)
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > 50) {
                Log.d("EditorView", "Tokenization took $elapsed ms for ${linesToTokenize.size} lines")
            }
        }

        var yPos = 10f + lineHeight

        // Draw line numbers
        if (showLineNumbers) {
            for (lineIndex in visibleStart until visibleEnd) {
                val lineNumber = (lineIndex + 1).toString()
                canvas.drawText(lineNumber, 4f, yPos, lineNumberPaint)
                yPos += lineHeight
            }
            yPos = 10f + lineHeight
        }

        // Draw find highlights
        if (findResults.isNotEmpty()) {
            for (position in findResults) {
                // Find the line and column for this position
                var lineStart = 0
                var lineIndex = 0
                for (i in textLines.indices) {
                    if (position < lineStart + textLines[i].length + 1) {
                        lineIndex = i
                        break
                    }
                    lineStart += textLines[i].length + 1
                }
                if (lineIndex in visibleRange) {
                    val offsetInLine = position - lineStart
                    val line = textLines[lineIndex]
                    val endOffset = min(offsetInLine + 20, line.length) // Approximate highlight length
                    val xPos = textStartX + defaultPaint.measureText(line.substring(0, offsetInLine))
                    canvas.drawRect(
                        xPos,
                        yPos - lineHeight + 4f,
                        xPos + defaultPaint.measureText(line.substring(offsetInLine, endOffset)),
                        yPos - 4f,
                        findHighlightPaint
                    )
                }
            }
        }

        // Draw text
        for (lineIndex in visibleStart until visibleEnd) {
            val line = textLines[lineIndex]
            val tokens = tokenCache[lineIndex] ?: listOf()
            var xPos = textStartX
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
                    val cursorX = textStartX + defaultPaint.measureText(beforeText)
                    canvas.drawLine(cursorX, yPos - lineHeight, cursorX, yPos, defaultPaint)
                }
            }
            yPos += lineHeight
        }

        val keysToRemove = tokenCache.keys.filter { it !in visibleRange }
        keysToRemove.forEach { tokenCache.remove(it) }
    }

    // Keyboard handling with undo/redo
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Ctrl+Z = Undo, Ctrl+Y = Redo
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    performUndo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    performRedo()
                    return true
                }
            }
        }

        // Ctrl+F = Find
        if (event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_F) {
            onFindRequested?.invoke()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (cursorPos > 0) {
                    val oldText = native?.getText() ?: ""
                    native?.deleteText(cursorPos - 1, 1)
                    cursorPos--
                    updateText()
                    // Record for undo (delete = insert the deleted char)
                    val newText = native?.getText() ?: ""
                    val deletedChar = oldText.substring(cursorPos, cursorPos + 1)
                    if (!isUndoRedoInProgress) {
                        undoManager.pushEdit(cursorPos, deletedChar, "")
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (cursorPos < (native?.getText()?.length ?: 0)) {
                    val oldText = native?.getText() ?: ""
                    val deletedChar = oldText.substring(cursorPos, cursorPos + 1)
                    native?.deleteText(cursorPos, 1)
                    updateText()
                    if (!isUndoRedoInProgress) {
                        undoManager.pushEdit(cursorPos, deletedChar, "")
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                native?.insertText(cursorPos, "\n")
                cursorPos++
                updateText()
                if (!isUndoRedoInProgress) {
                    undoManager.pushEdit(cursorPos - 1, "", "\n")
                }
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
                        if (!isUndoRedoInProgress) {
                            undoManager.pushEdit(cursorPos - 1, "", char.toString())
                        }
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

        return object : BaseInputConnection(this, true) {
            private var composingText = ""

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null) {
                    val newText = text.toString()
                    if (composingText.isNotEmpty()) {
                        val oldText = composingText
                        native?.deleteText(cursorPos - composingText.length, composingText.length)
                        cursorPos -= composingText.length
                        if (!isUndoRedoInProgress && oldText != newText) {
                            undoManager.pushEdit(cursorPos, oldText, newText)
                        }
                    } else {
                        if (!isUndoRedoInProgress && newText.isNotEmpty()) {
                            undoManager.pushEdit(cursorPos, "", newText)
                        }
                    }
                    composingText = newText
                    native?.insertText(cursorPos, newText)
                    cursorPos += newText.length
                    updateText()
                }
                return true
            }

            override fun finishComposingText(): Boolean {
                if (composingText.isNotEmpty()) {
                    composingText = ""
                    updateText()
                }
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null) {
                    val str = text.toString()
                    if (str.isNotEmpty()) {
                        native?.insertText(cursorPos, str)
                        cursorPos += str.length
                        updateText()
                        if (!isUndoRedoInProgress) {
                            undoManager.pushEdit(cursorPos - str.length, "", str)
                        }
                    }
                }
                return true
            }
        }
    }

    // --- Undo/Redo ---

    fun performUndo() {
        val action = undoManager.undo()
        if (action != null) {
            isUndoRedoInProgress = true
            // Apply undo: replace oldText at position
            val currentText = native?.getText() ?: ""
            // Delete current text at position
            val lenToDelete = action.newText.length
            if (lenToDelete > 0) {
                native?.deleteText(action.pos, lenToDelete)
            }
            // Insert old text
            if (action.oldText.isNotEmpty()) {
                native?.insertText(action.pos, action.oldText)
            }
            cursorPos = action.pos + action.oldText.length
            isUndoRedoInProgress = false
            updateText()
        }
    }

    fun performRedo() {
        val action = undoManager.redo()
        if (action != null) {
            isUndoRedoInProgress = true
            // Apply redo: replace oldText with newText at position
            val currentText = native?.getText() ?: ""
            // Delete old text
            val lenToDelete = action.oldText.length
            if (lenToDelete > 0) {
                native?.deleteText(action.pos, lenToDelete)
            }
            // Insert new text
            if (action.newText.isNotEmpty()) {
                native?.insertText(action.pos, action.newText)
            }
            cursorPos = action.pos + action.newText.length
            isUndoRedoInProgress = false
            updateText()
        }
    }

    fun canUndo(): Boolean = undoManager.canUndo()
    fun canRedo(): Boolean = undoManager.canRedo()

    var onUndoRedoStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // --- Find ---

    fun findNext(searchTerm: String, caseSensitive: Boolean) {
        val text = native?.getText() ?: return
        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        var startPos = cursorPos + 1
        if (startPos >= content.length) startPos = 0

        val index = content.indexOf(search, startPos)
        if (index != -1) {
            cursorPos = index
            // Highlight the found text by storing positions
            findResults = listOf(index)
            currentFindIndex = 0
            onFindResult?.invoke(index, searchTerm)
            invalidate()
        } else {
            // Wrap around
            val wrapIndex = content.indexOf(search)
            if (wrapIndex != -1) {
                cursorPos = wrapIndex
                findResults = listOf(wrapIndex)
                currentFindIndex = 0
                onFindResult?.invoke(wrapIndex, searchTerm)
                invalidate()
            } else {
                onFindNotFound?.invoke(searchTerm)
            }
        }
    }

    fun findPrevious(searchTerm: String, caseSensitive: Boolean) {
        val text = native?.getText() ?: return
        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        var startPos = cursorPos - 1
        if (startPos < 0) startPos = content.length - 1

        val index = content.lastIndexOf(search, startPos)
        if (index != -1) {
            cursorPos = index
            findResults = listOf(index)
            currentFindIndex = 0
            onFindResult?.invoke(index, searchTerm)
            invalidate()
        } else {
            // Wrap around
            val wrapIndex = content.lastIndexOf(search)
            if (wrapIndex != -1) {
                cursorPos = wrapIndex
                findResults = listOf(wrapIndex)
                currentFindIndex = 0
                onFindResult?.invoke(wrapIndex, searchTerm)
                invalidate()
            } else {
                onFindNotFound?.invoke(searchTerm)
            }
        }
    }

    fun replaceNext(searchTerm: String, replaceTerm: String, caseSensitive: Boolean) {
        val text = native?.getText() ?: return
        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        val index = content.indexOf(search, cursorPos)
        if (index != -1) {
            // Replace
            native?.deleteText(index, searchTerm.length)
            native?.insertText(index, replaceTerm)
            cursorPos = index + replaceTerm.length
            updateText()
            // Record undo
            undoManager.pushEdit(index, searchTerm, replaceTerm)
            // Clear find results
            findResults = emptyList()
            onFindReplaced?.invoke(index, replaceTerm)
            invalidate()
        } else {
            onFindNotFound?.invoke(searchTerm)
        }
    }

    fun replaceAll(searchTerm: String, replaceTerm: String, caseSensitive: Boolean) {
        val text = native?.getText() ?: return
        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        var count = 0
        var pos = 0
        while (true) {
            val index = content.indexOf(search, pos)
            if (index == -1) break
            // Replace
            native?.deleteText(index, searchTerm.length)
            native?.insertText(index, replaceTerm)
            // Update content for next search
            // Note: We need to rebuild the content string
            count++
            pos = index + replaceTerm.length
        }
        if (count > 0) {
            updateText()
            // Record as a single undo action (aggregate)
            undoManager.pushEdit(0, searchTerm, replaceTerm)
            onReplaceAllComplete?.invoke(count)
            invalidate()
        } else {
            onFindNotFound?.invoke(searchTerm)
        }
    }

    fun clearFindHighlights() {
        findResults = emptyList()
        currentFindIndex = -1
        invalidate()
    }

    // Callbacks for find/replace
    var onFindResult: ((position: Int, match: String) -> Unit)? = null
    var onFindNotFound: ((searchTerm: String) -> Unit)? = null
    var onFindReplaced: ((position: Int, replacement: String) -> Unit)? = null
    var onReplaceAllComplete: ((count: Int) -> Unit)? = null
    var onFindRequested: (() -> Unit)? = null

    // --- Cursor Management ---

    fun setCursorPos(pos: Int) {
        cursorPos = pos.coerceIn(0, native?.getText()?.length ?: 0)
        invalidate()
    }

    fun getCursorPos(): Int = cursorPos

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
        native = null
    }

    // --- Types ---

    enum class LexerTokenType {
        KEYWORD, IDENTIFIER, NUMBER, STRING, COMMENT, OPERATOR, PREPROCESSOR, DEFAULT
    }

    data class TokenInfo(val type: LexerTokenType, val length: Int)
}