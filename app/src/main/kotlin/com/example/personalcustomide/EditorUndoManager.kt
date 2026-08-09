package com.example.personalcustomide

import android.util.Log

/**
 * Manages undo/redo operations for the editor.
 * Stores edit actions as a stack of (position, oldText, newText).
 */
class EditorUndoManager {
    private val TAG = "EditorUndoManager"

    data class EditAction(
        val pos: Int,
        val oldText: String,
        val newText: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val undoStack = mutableListOf<EditAction>()
    private val redoStack = mutableListOf<EditAction>()
    private var maxSize = 100

    var onStateChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

    /**
     * Pushes a new edit onto the undo stack.
     * Clears the redo stack.
     */
    fun pushEdit(pos: Int, oldText: String, newText: String) {
        // Don't push empty changes
        if (oldText == newText) return

        val action = EditAction(pos, oldText, newText)
        undoStack.add(action)

        // Limit stack size
        if (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }

        // Clear redo stack when new action is performed
        redoStack.clear()

        Log.d(TAG, "Pushed edit at $pos: '${oldText.replace("\n", "\\n")}' -> '${newText.replace("\n", "\\n")}'")
        notifyStateChanged()
    }

    /**
     * Performs an undo operation.
     * @return The EditAction that was undone, or null if nothing to undo
     */
    fun undo(): EditAction? {
        if (undoStack.isEmpty()) return null

        val action = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(action)

        if (redoStack.size > maxSize) {
            redoStack.removeAt(0)
        }

        Log.d(TAG, "Undo: pos=${action.pos}, old='${action.oldText.replace("\n", "\\n")}'")
        notifyStateChanged()
        return action
    }

    /**
     * Performs a redo operation.
     * @return The EditAction that was redone, or null if nothing to redo
     */
    fun redo(): EditAction? {
        if (redoStack.isEmpty()) return null

        val action = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(action)

        if (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }

        Log.d(TAG, "Redo: pos=${action.pos}, new='${action.newText.replace("\n", "\\n")}'")
        notifyStateChanged()
        return action
    }

    /**
     * Checks if undo is available.
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * Checks if redo is available.
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /**
     * Clears the undo/redo stacks.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        notifyStateChanged()
    }

    /**
     * Gets the number of undo actions available.
     */
    fun undoCount(): Int = undoStack.size

    /**
     * Gets the number of redo actions available.
     */
    fun redoCount(): Int = redoStack.size

    /**
     * Sets the maximum stack size.
     */
    fun setMaxSize(size: Int) {
        maxSize = size.coerceAtLeast(10)
        while (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }
        while (redoStack.size > maxSize) {
            redoStack.removeAt(0)
        }
    }

    private fun notifyStateChanged() {
        onStateChanged?.invoke(canUndo(), canRedo())
    }
}