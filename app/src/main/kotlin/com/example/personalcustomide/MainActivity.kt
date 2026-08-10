package com.example.personalcustomide

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.personalcustomide.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomStatusText: TextView

    private var fileObserver: FileObserver? = null
    private var currentFilePath: String? = null
    private var isContentModified = false

    // Find/Replace
    private var findReplaceDialog: FindReplaceDialog? = null

    // Settings
    private var currentSettings = EditorSettings()

    // Keyboard shortcuts
    private lateinit var keyboardHandler: KeyboardShortcutHandler

    // Auto-save
    private val autoSaveManager = AutoSaveManager

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TERMUX_PERMISSION_REQUEST_CODE = 101
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bottomStatusText = binding.bottomStatusText

        if (savedInstanceState != null) {
            currentFilePath = savedInstanceState.getString("currentFilePath")
        }

        // Load settings
        currentSettings = EditorSettings.load(this)

        // Initialize auto-save
        AutoSaveManager.initialize(this)

        checkPermissions()

        if (!TermuxExecutor.isTermuxInstalled(this)) {
            showTermuxNotInstalledDialog()
        }

        TermuxExecutor.registerReceiver(this)

        lifecycleScope.launch {
            TermuxExecutor.results.collect { (commandId, result) ->
                runOnUiThread {
                    GitManager.processResult(commandId, result)
                    bottomStatusText.text = GitManager.status.value
                }
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fileExplorerContainer, FileExplorerFragment())
                .commit()
        }

        // Setup editor
        binding.editorView.setEditorNative(EditorNative)
        binding.editorView.applySettings(currentSettings)

        // Editor callbacks
        binding.editorView.onUndoRedoStateChanged = { canUndo, canRedo ->
            updateUndoRedoButtons(canUndo, canRedo)
        }
        binding.editorView.onFindRequested = { showFindReplaceDialog() }

        // Content change listener for auto-save
        binding.editorView.setOnContentChanged { content ->
            isContentModified = true
            AutoSaveManager.updateContent(content)
            // Mark tab as modified
            EditorTabManager.activeTab?.let { tab ->
                EditorTabManager.markActiveModified(true)
                updateTabUI()
            }
        }

        val defaultFile = filesDir.resolve("home/test.txt")
        val fileToLoad = currentFilePath ?: defaultFile.absolutePath

        // Open in tab manager
        EditorTabManager.onTabChanged = { tab ->
            tab?.let {
                currentFilePath = it.filePath
                lifecycleScope.launch {
                    loadFileAsync(it.filePath)
                }
                updateTabUI()
            }
        }
        EditorTabManager.onTabsUpdated = {
            updateTabUI()
        }

        // Check for draft before loading
        checkForDraft(fileToLoad)

        lifecycleScope.launch { loadFileAsync(fileToLoad) }

        // Setup keyboard shortcuts
        setupKeyboardShortcuts()

        // Setup bottom panel buttons
        binding.btnGitStatus.setOnClickListener {
            val workingDir = filesDir.resolve("home").absolutePath
            GitManager.gitStatus(this, workingDir)
        }
        binding.btnGitCommit.setOnClickListener { showCommitDialog() }
        binding.btnGitPush.setOnClickListener {
            val workingDir = filesDir.resolve("home").absolutePath
            GitManager.gitPush(this, workingDir)
        }
        binding.btnCloneRepo.setOnClickListener { showCloneDialog() }
        binding.btnCreatePython.setOnClickListener {
            val workingDir = filesDir.resolve("home").absolutePath
            GitManager.createPythonProject(this, "$workingDir/my_python_project")
        }

        // Undo/Redo buttons
        binding.btnUndo.setOnClickListener {
            binding.editorView.performUndo()
        }
        binding.btnRedo.setOnClickListener {
            binding.editorView.performRedo()
        }

        // Find button
        binding.btnFind.setOnClickListener {
            showFindReplaceDialog()
        }

        // New tab button
        binding.btnNewTab.setOnClickListener {
            showNewFileDialog()
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        val workingDir = filesDir.resolve("home").absolutePath
        GitManager.gitStatus(this, workingDir)
        setupFileObserver(fileToLoad)

        // Initial UI update
        updateTabUI()
        updateUndoRedoButtons(false, false)

        // Start auto-save
        startAutoSave()
    }

    // --- Keyboard Shortcuts ---

    private fun setupKeyboardShortcuts() {
        keyboardHandler = KeyboardShortcutHandler(binding.editorView).apply {
            callback = object : KeyboardShortcutHandler.Callback {
                override fun onSave() { saveCurrentFile() }
                override fun onFind() { showFindReplaceDialog() }
                override fun onFindNext() {
                    // Reuse last search term
                    binding.editorView.findNext(lastSearchTerm, lastCaseSensitive)
                }
                override fun onFindPrevious() {
                    binding.editorView.findPrevious(lastSearchTerm, lastCaseSensitive)
                }
                override fun onReplace() { showFindReplaceDialog() }
                override fun onUndo() { binding.editorView.performUndo() }
                override fun onRedo() { binding.editorView.performRedo() }
                override fun onSelectAll() { /* Implement select all */ }
                override fun onCopy() { /* Implement copy */ }
                override fun onCut() { /* Implement cut */ }
                override fun onPaste() { /* Implement paste */ }
                override fun onNewFile() { showNewFileDialog() }
                override fun onOpenFile() { /* Open file picker */ }
                override fun onCloseFile() { closeCurrentTab() }
                override fun onToggleLineNumbers() { binding.editorView.toggleLineNumbers() }
                override fun onToggleWordWrap() { binding.editorView.toggleWordWrap() }
                override fun onZoomIn() { binding.editorView.zoomIn() }
                override fun onZoomOut() { binding.editorView.zoomOut() }
                override fun onZoomReset() { binding.editorView.zoomReset() }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Let keyboard handler process first
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handleKeyEvent(binding.editorView, keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // --- Auto-Save ---

    private fun startAutoSave() {
        val filePath = currentFilePath ?: return
        val content = binding.editorView.getContentForAutoSave()
        AutoSaveManager.start(
            this,
            filePath,
            content,
            currentSettings.autoSaveInterval
        )
        AutoSaveManager.setAutoSaveEnabled(currentSettings.autoSave)
    }

    private fun stopAutoSave() {
        AutoSaveManager.stop()
    }

    private fun checkForDraft(filePath: String) {
        if (AutoSaveManager.hasDraft(this, filePath)) {
            AlertDialog.Builder(this)
                .setTitle("Draft Found")
                .setMessage("An unsaved draft exists for this file. Load it?")
                .setPositiveButton("Load Draft") { _, _ ->
                    val draft = AutoSaveManager.getDraft(this, filePath)
                    draft?.let {
                        // Load draft content into editor
                        // For now, we just show a toast
                        Toast.makeText(this, "Draft loaded", Toast.LENGTH_SHORT).show()
                        // In a full implementation, you'd set the editor content here
                    }
                }
                .setNegativeButton("Discard") { _, _ ->
                    AutoSaveManager.deleteDraft(this, filePath)
                }
                .setNeutralButton("Compare", null)
                .show()
        }
    }

    // --- Settings ---

    private fun showSettingsDialog() {
        SettingsDialog(this).show(currentSettings) { newSettings ->
            currentSettings = newSettings
            EditorSettings.save(this, newSettings)
            binding.editorView.applySettings(newSettings)
            // Update auto-save settings
            AutoSaveManager.setAutoSaveEnabled(newSettings.autoSave)
            AutoSaveManager.setAutoSaveInterval(newSettings.autoSaveInterval)
            Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
        }
    }

    // --- File Operations ---

    private fun saveCurrentFile() {
        val filePath = EditorTabManager.getActiveFilePath()
        if (filePath != null) {
            val content = binding.editorView.getContentForAutoSave()
            if (AutoSaveManager.saveToFile(filePath, content)) {
                isContentModified = false
                EditorTabManager.markActiveModified(false)
                updateTabUI()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                // Delete draft after successful save
                AutoSaveManager.deleteDraft(this, filePath)
            }
        }
    }

    private fun closeCurrentTab() {
        if (isContentModified) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save before closing?")
                .setPositiveButton("Save") { _, _ ->
                    saveCurrentFile()
                    EditorTabManager.closeActiveTab()
                }
                .setNegativeButton("Discard") { _, _ ->
                    EditorTabManager.closeActiveTab()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            EditorTabManager.closeActiveTab()
        }
    }

    // --- Tab Management ---

    private fun updateTabUI() {
        val tabContainer = binding.tabContainer
        tabContainer.removeAllViews()

        for ((index, tab) in EditorTabManager.tabs.withIndex()) {
            val tabView = layoutInflater.inflate(R.layout.tab_item, tabContainer, false)
            val titleView = tabView.findViewById<TextView>(R.id.tabTitle)
            val closeView = tabView.findViewById<View>(R.id.tabClose)

            titleView.text = tab.fileName
            if (tab.isModified) {
                titleView.text = "${tab.fileName} *"
                titleView.setTextColor(0xFF2196F3.toInt())
            } else {
                titleView.setTextColor(0xFF333333.toInt())
            }

            // Highlight active tab
            if (index == EditorTabManager.activeTabIndex) {
                tabView.setBackgroundColor(0xFFE3F2FD.toInt())
            } else {
                tabView.setBackgroundColor(0x00000000.toInt())
            }

            tabView.setOnClickListener {
                EditorTabManager.switchToTab(index)
            }

            closeView.setOnClickListener {
                closeTabWithCheck(index)
            }

            tabContainer.addView(tabView)
        }

        // Show/hide tab container based on tab count
        binding.tabContainer.visibility = if (EditorTabManager.tabCount > 0) View.VISIBLE else View.GONE
    }

    private fun closeTabWithCheck(index: Int) {
        val tab = EditorTabManager.tabs.getOrNull(index)
        if (tab != null && tab.isModified) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save before closing?")
                .setPositiveButton("Save") { _, _ ->
                    saveCurrentFile()
                    EditorTabManager.closeTab(index)
                    updateTabUI()
                }
                .setNegativeButton("Discard") { _, _ ->
                    EditorTabManager.closeTab(index)
                    updateTabUI()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            EditorTabManager.closeTab(index)
            updateTabUI()
        }
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        binding.btnUndo.isEnabled = canUndo
        binding.btnUndo.alpha = if (canUndo) 1.0f else 0.5f
        binding.btnRedo.isEnabled = canRedo
        binding.btnRedo.alpha = if (canRedo) 1.0f else 0.5f
    }

    // --- Find/Replace ---

    private var lastSearchTerm = ""
    private var lastCaseSensitive = false

    private fun showFindReplaceDialog() {
        if (findReplaceDialog == null) {
            findReplaceDialog = FindReplaceDialog(this)
        }
        findReplaceDialog?.show(object : FindReplaceDialog.Callback {
            override fun onFindNext(searchTerm: String, caseSensitive: Boolean) {
                lastSearchTerm = searchTerm
                lastCaseSensitive = caseSensitive
                binding.editorView.findNext(searchTerm, caseSensitive)
            }

            override fun onFindPrevious(searchTerm: String, caseSensitive: Boolean) {
                lastSearchTerm = searchTerm
                lastCaseSensitive = caseSensitive
                binding.editorView.findPrevious(searchTerm, caseSensitive)
            }

            override fun onReplaceAll(searchTerm: String, replaceTerm: String, caseSensitive: Boolean) {
                binding.editorView.replaceAll(searchTerm, replaceTerm, caseSensitive)
            }

            override fun onReplaceNext(searchTerm: String, replaceTerm: String, caseSensitive: Boolean) {
                binding.editorView.replaceNext(searchTerm, replaceTerm, caseSensitive)
            }

            override fun onClose() {
                binding.editorView.clearFindHighlights()
                findReplaceDialog = null
            }
        })
    }

    // --- File Loading ---

    private suspend fun loadFileAsync(path: String) = withContext(Dispatchers.IO) {
        // Use lazy loading for large files
        val success = if (LargeFileLoader().isLargeFile(path)) {
            binding.editorView.loadFileLazy(path)
        } else {
            EditorNative.loadFile(path)
        }
        withContext(Dispatchers.Main) {
            if (success) {
                currentFilePath = path
                // Add to tab manager
                EditorTabManager.openFile(path)
                binding.editorView.invalidate()
                setupFileObserver(path)
                updateTabUI()
                // Restart auto-save for new file
                startAutoSave()
            } else {
                Toast.makeText(this@MainActivity, "Failed to load file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- File Observer ---

    private fun setupFileObserver(path: String) {
        fileObserver?.stopWatching()
        fileObserver = object : FileObserver(path) {
            override fun onEvent(event: Int, path: String?) {
                if (event == MODIFY || event == CLOSE_WRITE) {
                    runOnUiThread {
                        lifecycleScope.launch {
                            path?.let { loadFileAsync(it) }
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()
    }

    // --- Dialog Helpers ---

    private fun showNewFileDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Enter file name (e.g., main.py)"
        AlertDialog.Builder(this)
            .setTitle("New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val file = filesDir.resolve("home").resolve(fileName)
                    try {
                        if (file.createNewFile()) {
                            Toast.makeText(this, "Created $fileName", Toast.LENGTH_SHORT).show()
                            EditorTabManager.openFile(file.absolutePath)
                        } else {
                            Toast.makeText(this, "File already exists", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCommitDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Commit message"
        AlertDialog.Builder(this)
            .setTitle("Commit Changes")
            .setView(input)
            .setPositiveButton("Commit") { _, _ ->
                val msg = input.text.toString().trim()
                if (msg.isNotEmpty()) {
                    val workingDir = filesDir.resolve("home").absolutePath
                    GitManager.gitCommit(this, workingDir, msg)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCloneDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Git repository URL (e.g., https://github.com/user/repo.git)"
        AlertDialog.Builder(this)
            .setTitle("Clone Repository")
            .setView(input)
            .setPositiveButton("Clone") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    val workingDir = filesDir.resolve("home").absolutePath
                    val repoName = url.substringAfterLast('/').removeSuffix(".git")
                    GitManager.gitClone(this, workingDir, url, repoName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTermuxNotInstalledDialog() {
        AlertDialog.Builder(this)
            .setTitle("Termux Required")
            .setMessage("This app needs Termux to run commands. Please install it from F-Droid or the Play Store.")
            .setPositiveButton("Open F-Droid") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage("This app needs permission to run commands in Termux to execute Git and other tools.")
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(TermuxExecutor.PERMISSION_RUN_COMMAND),
                    TERMUX_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    // --- Permissions ---

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val storagePerms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val missing = storagePerms.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        }

        if (!TermuxExecutor.hasRunCommandPermission(this)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, TermuxExecutor.PERMISSION_RUN_COMMAND)) {
                showPermissionRationale()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(TermuxExecutor.PERMISSION_RUN_COMMAND),
                    TERMUX_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            TERMUX_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "RUN_COMMAND permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Permission required for Git and shell commands", Toast.LENGTH_LONG).show()
                    openAppSettings()
                }
            }
        }
    }

    // --- Lifecycle ---

    override fun onPause() {
        super.onPause()
        // Auto-save on pause
        val content = binding.editorView.getContentForAutoSave()
        val filePath = EditorTabManager.getActiveFilePath()
        if (filePath != null && content.isNotEmpty()) {
            AutoSaveManager.saveDraft(this, filePath, content)
        }
        stopAutoSave()
    }

    override fun onResume() {
        super.onResume()
        startAutoSave()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentFilePath?.let { outState.putString("currentFilePath", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        stopAutoSave()
        TermuxExecutor.unregisterReceiver(this)
        findReplaceDialog?.dismiss()
    }

    // --- Public Methods for Fragments ---

    fun updateEditor() {
        binding.editorView.invalidate()
    }
}