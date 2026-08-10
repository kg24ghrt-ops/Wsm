package com.example.personalcustomide

import android.view.KeyEvent
import android.view.View

class KeyboardShortcutHandler(private val editorView: EditorView) {

    interface Callback {
        fun onSave()
        fun onFind()
        fun onFindNext()
        fun onFindPrevious()
        fun onReplace()
        fun onUndo()
        fun onRedo()
        fun onSelectAll()
        fun onCopy()
        fun onCut()
        fun onPaste()
        fun onNewFile()
        fun onOpenFile()
        fun onCloseFile()
        fun onToggleLineNumbers()
        fun onToggleWordWrap()
        fun onZoomIn()
        fun onZoomOut()
        fun onZoomReset()
    }

    var callback: Callback? = null

    fun handleKeyEvent(view: View, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed

        // Ctrl+Key combinations
        if (isCtrl) {
            when (keyCode) {
                KeyEvent.KEYCODE_S -> {
                    callback?.onSave()
                    return true
                }
                KeyEvent.KEYCODE_F -> {
                    callback?.onFind()
                    return true
                }
                KeyEvent.KEYCODE_G -> {
                    if (isShift) {
                        callback?.onFindPrevious()
                    } else {
                        callback?.onFindNext()
                    }
                    return true
                }
                KeyEvent.KEYCODE_H -> {
                    callback?.onReplace()
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    callback?.onUndo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    callback?.onRedo()
                    return true
                }
                KeyEvent.KEYCODE_A -> {
                    callback?.onSelectAll()
                    return true
                }
                KeyEvent.KEYCODE_C -> {
                    callback?.onCopy()
                    return true
                }
                KeyEvent.KEYCODE_X -> {
                    callback?.onCut()
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    callback?.onPaste()
                    return true
                }
                KeyEvent.KEYCODE_N -> {
                    callback?.onNewFile()
                    return true
                }
                KeyEvent.KEYCODE_O -> {
                    callback?.onOpenFile()
                    return true
                }
                KeyEvent.KEYCODE_W -> {
                    callback?.onCloseFile()
                    return true
                }
                KeyEvent.KEYCODE_L -> {
                    callback?.onToggleLineNumbers()
                    return true
                }
                KeyEvent.KEYCODE_EQUALS -> {
                    callback?.onZoomIn()
                    return true
                }
                KeyEvent.KEYCODE_MINUS -> {
                    callback?.onZoomOut()
                    return true
                }
                KeyEvent.KEYCODE_0 -> {
                    callback?.onZoomReset()
                    return true
                }
            }
        }

        // Ctrl+Shift+Key combinations
        if (isCtrl && isShift) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> {
                    callback?.onRedo()
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    // Save As
                    return true
                }
            }
        }

        return false
    }
}