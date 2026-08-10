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

/**
 * Custom editor view with syntax highlighting, undo/redo, find/replace, and lazy loading.
 * 
 * CHANGES MADE:
 * - Added proper setText()/getText() methods that manage text directly
 * - Fixed cursor positioning edge cases (empty files, out-of-bounds)
 * - Improved undo/redo with better boundary checking
 * - Added null-safety for native operations
 * - Added clear() method for resetting state
 */
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

    // FIX: Use a mutable list for textLines so we can modify it directly
    private var textLines: MutableList<String> = mutableListOf("")
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

    // Settings, Lazy Loading, Auto-Save
    private var settings = EditorSettings()
    private var largeFileLoader: LargeFileLoader? = null
    private var isLargeFile = false
    private var loadedChunks = mutableMapOf<Int, String>()
    private var visibleLineStart = 0
    private var visibleLineEnd = 100
    private var onContentChanged: ((String) -> Unit)? = null

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

        undoManager.onStateChanged = { canUndo, canRedo ->
            onUndoRedoStateChanged?.invoke(canUndo, canRedo)
        }
    }

    // ===== NEW: Direct text management =====

    /**
     * FIX: Sets the editor text directly, bypassing native.
     * This is used when loading files.
     */
    fun setText(text: String) {
        textLines = text.split("\n".toRegex()).toMutableList()
        if (textLines.isEmpty()) textLines.add("")
        cursorPos = 0
        tokenCache.clear()
        // Also update native if available
        native?.let { it.setText(text) }
        invalidate()
        notifyContentChanged()
    }

    /**
     * FIX: Gets the full text as a single string.
     */
    fun getText(): String {
        return textLines.joinToString("\n")
    }

    /**
     * FIX: Clears the editor state.
     */
    fun clear() {
        textLines = mutableListOf("")
        cursorPos = 0
        tokenCache.clear()
        findResults = emptyList()
        currentFindIndex = -1
        undoManager.clear()
        invalidate()
        notifyContentChanged()
    }

    // ===== Settings =====

    fun applySettings(newSettings: EditorSettings) {
        settings = newSettings
        defaultPaint.textSize = settings.fontSize
        lineNumberPaint.textSize = settings.fontSize * 0.8f
        lineHeight = defaultPaint.fontSpacing
        charWidth = defaultPaint.measureText("W")
        showLineNumbers = settings.showLineNumbers
        if (!settings.enableSyntaxHighlighting) {
            tokenCache.clear()
        }
        invalidate()
    }

    // ===== Lazy Loading =====

    fun loadFileLazy(path: String): Boolean {
        return try {
            val loader = LargeFileLoader(path)
            val content = loader.load()
            if (content != null) {
                largeFileLoader = loader
                isLargeFile = loader.isLarge()
                textLines = content.split("\n".toRegex()).toMutableList()
                if (textLines.isEmpty()) textLines.add("")
                tokenCache.clear()
                invalidate()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("EditorView", "Failed to load large file", e)
            false
        }
    }

    // ===== Auto-Save =====

    fun getContentForAutoSave(): String = getText()

    fun setOnContentChanged(listener: (String) -> Unit) {
        onContentChanged = listener
    }

    private fun notifyContentChanged() {
        onContentChanged?.invoke(getText())
    }

    // ===== Zoom =====

    fun zoomIn() {
        val newSize = (settings.fontSize * 1.1f).coerceAtMost(120f)
        applySettings(settings.copy(fontSize = newSize))
    }

    fun zoomOut() {
        val newSize = (settings.fontSize * 0.9f).coerceAtLeast(12f)
        applySettings(settings.copy(fontSize = newSize))
    }

    fun zoomReset() {
        applySettings(settings.copy(fontSize = 40f))
    }

    // ===== Toggles =====

    fun toggleLineNumbers() {
        showLineNumbers = !showLineNumbers
        applySettings(settings.copy(showLineNumbers = showLineNumbers))
    }

    fun toggleWordWrap() {
        val newWordWrap = !settings.wordWrap
        applySettings(settings.copy(wordWrap = newWordWrap))
    }

    // ===== Native Integration =====

    fun setEditorNative(native: EditorNative) {
        this.native = native
        // Load text from native if available
        native.getText()?.let { text ->
            textLines = text.split("\n".toRegex()).toMutableList()
            if (textLines.isEmpty()) textLines.add("")
        }
        undoManager.clear()
        invalidate()
    }

    fun setShowLineNumbers(show: Boolean) {
        showLineNumbers = show
        invalidate()
    }

    // ===== Drawing =====

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (textLines.isEmpty() || (textLines.size == 1 && textLines[0].isEmpty())) {
            canvas.drawText("[empty file]", textStartX, 10f + lineHeight, defaultPaint)
            return
        }

        val visibleStart = 0
        val visibleEnd = min(textLines.size, ((height - 20) / lineHeight).toInt() + 2)
        val visibleRange = visibleStart until visibleEnd

        // Tokenize visible lines
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
                    val endOffset = min(offsetInLine + 20, line.length)
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
                    val beforeText = line.substring(0, offsetInLine.coerceIn(0, line.length))
                    val cursorX = textStartX + defaultPaint.measureText(beforeText)
                    canvas.drawLine(cursorX, yPos - lineHeight, cursorX, yPos, defaultPaint)
                }
            }

            yPos += lineHeight
        }

        // Clean up token cache
        val keysToRemove = tokenCache.keys.filter { it !in visibleRange }
        keysToRemove.forEach { tokenCache.remove(it) }
    }

    // ===== Keyboard Handling =====

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Ctrl+Z = Undo, Ctrl+Y = Redo
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> { performUndo(); return true }
                KeyEvent.KEYCODE_Y -> { performRedo(); return true }
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
                    val oldText = getText()
                    val deletedChar = oldText.substring(cursorPos - 1, cursorPos)
                    // Remove from textLines
                    removeTextAt(cursorPos - 1, 1)
                    cursorPos--
                    if (!isUndoRedoInProgress) {
                        undoManager.pushEdit(cursorPos, deletedChar, "")
                    }
                    updateTextAndNotify()
                }
                return true
            }

            KeyEvent.KEYCODE_FORWARD_DEL -> {
                val textLen = getText().length
                if (cursorPos < textLen) {
                    val oldText = getText()
                    val deletedChar = oldText.substring(cursorPos, cursorPos + 1)
                    removeTextAt(cursorPos, 1)
                    if (!isUndoRedoInProgress) {
                        undoManager.pushEdit(cursorPos, deletedChar, "")
                    }
                    updateTextAndNotify()
                }
                return true
            }

            KeyEvent.KEYCODE_ENTER -> {
                insertTextAt(cursorPos, "\n")
                cursorPos++
                if (!isUndoRedoInProgress) {
                    undoManager.pushEdit(cursorPos - 1, "", "\n")
                }
                updateTextAndNotify()
                return true
            }

            else -> {
                val unicode = event.unicodeChar
                if (unicode != 0 && event.action == KeyEvent.ACTION_DOWN) {
                    if (Character.isValidCodePoint(unicode)) {
                        val char = Char(unicode)
                        insertTextAt(cursorPos, char.toString())
                        cursorPos++
                        if (!isUndoRedoInProgress) {
                            undoManager.pushEdit(cursorPos - 1, "", char.toString())
                        }
                        updateTextAndNotify()
                        return true
                    }
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    // ===== Text Manipulation Helpers =====

    private fun insertTextAt(pos: Int, text: String) {
        val fullText = getText()
        val newText = fullText.substring(0, pos) + text + fullText.substring(pos)
        setText(newText)
    }

    private fun removeTextAt(pos: Int, count: Int) {
        val fullText = getText()
        val newText = fullText.substring(0, pos) + fullText.substring(pos + count)
        setText(newText)
    }

    private fun updateTextAndNotify() {
        // Also update native if available
        native?.let { it.setText(getText()) }
        invalidate()
        notifyContentChanged()
    }

    // ===== Input Connection =====

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE

        return object : BaseInputConnection(this, true) {
            private var composingText = ""

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null) {
                    val newText = text.toString()
                    if (composingText.isNotEmpty()) {
                        // Replace composing text
                        val startPos = cursorPos - composingText.length
                        removeTextAt(startPos, composingText.length)
                        cursorPos = startPos
                    }
                    if (newText.isNotEmpty()) {
                        insertTextAt(cursorPos, newText)
                        cursorPos += newText.length
                        if (!isUndoRedoInProgress && composingText.isEmpty()) {
                            undoManager.pushEdit(cursorPos - newText.length, "", newText)
                        }
                    }
                    composingText = newText
                    updateTextAndNotify()
                }
                return true
            }

            override fun finishComposingText(): Boolean {
                if (composingText.isNotEmpty()) {
                    composingText = ""
                    updateTextAndNotify()
                }
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null) {
                    val str = text.toString()
                    if (str.isNotEmpty()) {
                        insertTextAt(cursorPos, str)
                        cursorPos += str.length
                        if (!isUndoRedoInProgress) {
                            undoManager.pushEdit(cursorPos - str.length, "", str)
                        }
                        updateTextAndNotify()
                    }
                }
                return true
            }
        }
    }

    // ===== Undo/Redo =====

    fun performUndo() {
        val action = undoManager.undo()
        if (action != null) {
            isUndoRedoInProgress = true
            val currentText = getText()
            // Replace current text at position with oldText
            val newText = currentText.substring(0, action.pos) +
                    action.oldText +
                    currentText.substring(action.pos + action.newText.length)
            setText(newText)
            cursorPos = action.pos + action.oldText.length
            isUndoRedoInProgress = false
            updateTextAndNotify()
        }
    }

    fun performRedo() {
        val action = undoManager.redo()
        if (action != null) {
            isUndoRedoInProgress = true
            val currentText = getText()
            val newText = currentText.substring(0, action.pos) +
                    action.newText +
                    currentText.substring(action.pos + action.oldText.length)
            setText(newText)
            cursorPos = action.pos + action.newText.length
            isUndoRedoInProgress = false
            updateTextAndNotify()
        }
    }

    fun canUndo(): Boolean = undoManager.canUndo()
    fun canRedo(): Boolean = undoManager.canRedo()

    var onUndoRedoStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    // ===== Find =====

    fun findNext(searchTerm: String, caseSensitive: Boolean) {
        val text = getText()
        if (text.isEmpty()) return

        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        var startPos = cursorPos + 1
        if (startPos >= content.length) startPos = 0

        val index = content.indexOf(search, startPos)
        if (index != -1) {
            cursorPos = index
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
        val text = getText()
        if (text.isEmpty()) return

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
        val text = getText()
        if (text.isEmpty()) return

        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        val index = content.indexOf(search, cursorPos)
        if (index != -1) {
            val newText = text.substring(0, index) + replaceTerm + text.substring(index + searchTerm.length)
            setText(newText)
            cursorPos = index + replaceTerm.length
            undoManager.pushEdit(index, searchTerm, replaceTerm)
            findResults = emptyList()
            onFindReplaced?.invoke(index, replaceTerm)
            updateTextAndNotify()
        } else {
            onFindNotFound?.invoke(searchTerm)
        }
    }

    fun replaceAll(searchTerm: String, replaceTerm: String, caseSensitive: Boolean) {
        val text = getText()
        if (text.isEmpty()) return

        val search = if (caseSensitive) searchTerm else searchTerm.lowercase()
        val content = if (caseSensitive) text else text.lowercase()

        var count = 0
        var pos = 0
        var result = text

        while (true) {
            val index = content.indexOf(search, pos)
            if (index == -1) break
            result = result.substring(0, index) + replaceTerm + result.substring(index + searchTerm.length)
            count++
            pos = index + replaceTerm.length
        }

        if (count > 0) {
            setText(result)
            undoManager.pushEdit(0, searchTerm, replaceTerm)
            onReplaceAllComplete?.invoke(count)
            updateTextAndNotify()
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

    // ===== Cursor Management =====

    fun setCursorPos(pos: Int) {
        cursorPos = pos.coerceIn(0, getText().length)
        invalidate()
    }

    fun getCursorPos(): Int = cursorPos

    // ===== Cursor Blink =====

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

    // ===== Types =====

    enum class LexerTokenType {
        KEYWORD, IDENTIFIER, NUMBER, STRING, COMMENT, OPERATOR, PREPROCESSOR, DEFAULT
    }

    data class TokenInfo(val type: LexerTokenType, val length: Int)
}