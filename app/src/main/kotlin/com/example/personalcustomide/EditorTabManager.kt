package com.example.personalcustomide

import android.util.Log
import java.io.File

/**
 * Manages multiple open files with tab support.
 * Tracks which files are open, which is active, and provides switching.
 */
object EditorTabManager {
    private const val TAG = "EditorTabManager"

    data class Tab(val filePath: String, val fileName: String, var isModified: Boolean = false)

    private val _tabs = mutableListOf<Tab>()
    private var _activeTabIndex: Int = -1

    val tabs: List<Tab> get() = _tabs.toList()
    val activeTabIndex: Int get() = _activeTabIndex
    val activeTab: Tab? get() = if (_activeTabIndex in _tabs.indices) _tabs[_activeTabIndex] else null
    val tabCount: Int get() = _tabs.size

    var onTabChanged: ((Tab?) -> Unit)? = null
    var onTabsUpdated: (() -> Unit)? = null

    /**
     * Opens a file in a new tab. If already open, switches to it.
     * @return true if file was opened, false if failed
     */
    fun openFile(filePath: String): Boolean {
        // Check if already open
        val existingIndex = _tabs.indexOfFirst { it.filePath == filePath }
        if (existingIndex != -1) {
            switchToTab(existingIndex)
            return true
        }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: $filePath")
            return false
        }

        val tab = Tab(filePath, file.name)
        _tabs.add(tab)
        _activeTabIndex = _tabs.size - 1

        Log.d(TAG, "Opened file: ${file.name} (${_tabs.size} tabs)")
        notifyListeners()
        return true
    }

    /**
     * Switches to a tab by index.
     */
    fun switchToTab(index: Int) {
        if (index !in _tabs.indices) return
        if (_activeTabIndex == index) return

        // Save current tab's content if needed (handled by caller)
        _activeTabIndex = index
        Log.d(TAG, "Switched to tab: ${_tabs[index].fileName}")
        notifyListeners()
    }

    /**
     * Closes a tab by index.
     * @return true if closed, false if it was the last tab
     */
    fun closeTab(index: Int): Boolean {
        if (index !in _tabs.indices) return false
        if (_tabs.size <= 1) {
            Log.d(TAG, "Cannot close last tab")
            return false
        }

        _tabs.removeAt(index)
        if (_activeTabIndex == index) {
            _activeTabIndex = if (index < _tabs.size) index else index - 1
        } else if (_activeTabIndex > index) {
            _activeTabIndex--
        }
        Log.d(TAG, "Closed tab, ${_tabs.size} remaining")
        notifyListeners()
        return true
    }

    /**
     * Closes the active tab.
     */
    fun closeActiveTab(): Boolean {
        if (_activeTabIndex == -1 || _tabs.isEmpty()) return false
        return closeTab(_activeTabIndex)
    }

    /**
     * Marks the active tab as modified.
     */
    fun markActiveModified(modified: Boolean) {
        activeTab?.let {
            it.isModified = modified
            notifyListeners()
        }
    }

    /**
     * Gets the file path of the active tab.
     */
    fun getActiveFilePath(): String? = activeTab?.filePath

    /**
     * Gets all open file paths.
     */
    fun getOpenFilePaths(): List<String> = _tabs.map { it.filePath }

    /**
     * Checks if a file is already open.
     */
    fun isFileOpen(filePath: String): Boolean {
        return _tabs.any { it.filePath == filePath }
    }

    /**
     * Closes all tabs and resets state.
     */
    fun closeAllTabs() {
        _tabs.clear()
        _activeTabIndex = -1
        notifyListeners()
    }

    private fun notifyListeners() {
        onTabsUpdated?.invoke()
        onTabChanged?.invoke(activeTab)
    }
}