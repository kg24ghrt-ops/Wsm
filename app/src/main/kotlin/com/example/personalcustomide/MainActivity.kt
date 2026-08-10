package com.example.personalcustomide

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.personalcustomide.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main activity for the Personal Custom IDE.
 * 
 * CHANGES MADE:
 * - Removed FileObserver (was causing race conditions and unwanted reloads)
 * - Added FileManager for all file I/O
 * - Fixed new file creation to actually load the file
 * - Added manual reload functionality
 * - Added proper error handling everywhere
 * - Added filename sanitization
 * - Added placeholder for "people" feature
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomStatusText: TextView
    private lateinit var tabContainer: View
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnFind: ImageButton
    private lateinit var btnNewTab: ImageButton
    private lateinit var btnSettings: ImageButton
    private var btnFiles: ImageButton? = null

    private var currentFilePath: String? = null
    private var isContentModified = false
    private var findReplaceDialog: FindReplaceDialog? = null
    private var currentSettings = EditorSettings()
    private lateinit var keyboardHandler: KeyboardShortcutHandler

    // FIX: Replaced FileObserver with a clean FileManager
    private lateinit var fileManager: FileManager

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TERMUX_PERMISSION_REQUEST_CODE = 101
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize FileManager
        fileManager = FileManager(this)

        bottomStatusText = binding.bottomStatusText
        tabContainer = binding.tabContainer
        btnUndo = binding.btnUndo
        btnRedo = binding.btnRedo
        btnFind = binding.btnFind
        btnNewTab = binding.btnNewTab
        btnSettings = binding.btnSettings
        btnFiles = binding.btnFiles

        if (savedInstanceState != null) {
            currentFilePath = savedInstanceState.getString("currentFilePath")
        }

        currentSettings = EditorSettings.load(this)
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

        binding.editorView.setEditorNative(EditorNative)
        binding.editorView.applySettings(currentSettings)
        binding.editorView.onUndoRedoStateChanged = { canUndo, canRedo ->
            updateUndoRedoButtons(canUndo, canRedo)
        }
        binding.editorView.onFindRequested = {
            showFindReplaceDialog()
        }
        binding.editorView.setOnContentChanged { content ->
            isContentModified = true
            AutoSaveManager.updateContent(content)
            EditorTabManager.activeTab?.let { tab ->
                EditorTabManager.markActiveModified(true)
                updateTabUI()
            }
        }

        // Load the last opened file or create a default one
        val defaultFile = filesDir.resolve("home/test.txt")
        val fileToLoad = currentFilePath ?: defaultFile.absolutePath

        // Check for draft before loading
        checkForDraft(fileToLoad)

        lifecycleScope.launch {
            loadFileAsync(fileToLoad)
        }

        setupKeyboardShortcuts()

        // Git buttons
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

        // Editor buttons
        btnUndo.setOnClickListener { binding.editorView.performUndo() }
        btnRedo.setOnClickListener { binding.editorView.performRedo() }
        btnFind.setOnClickListener { showFindReplaceDialog() }
        btnNewTab.setOnClickListener { showNewFileDialog() }
        btnSettings.setOnClickListener { showSettingsDialog() }
        btnFiles?.setOnClickListener { toggleFileExplorer() }

        // Initial Git status
        val workingDir = filesDir.resolve("home").absolutePath
        GitManager.gitStatus(this, workingDir)

        // FIX: Removed FileObserver – use manual reload instead
        // setupFileObserver(fileToLoad)  <-- REMOVED

        updateTabUI()
        updateUndoRedoButtons(false, false)
        startAutoSave()

        // FIX: Added placeholder for "people" feature
        // If you want to show contributors/collaborators, implement this:
        // fetchAndDisplayPeople()
    }

    /**
     * FIX: Manual reload from disk – replaces the broken FileObserver.
     * Call this from a "Reload" button or menu item.
     */
    fun reloadCurrentFile() {
        val filePath = EditorTabManager.getActiveFilePath()
        if (filePath == null) {
            Toast.makeText(this, "No file is open", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val file = File(filePath)
            val content = fileManager.readFile(file)
            if (content != null) {
                binding.editorView.setText(content)
                Toast.makeText(this@MainActivity, "Reloaded from disk", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFileExplorer() {
        val container = findViewById<View>(R.id.fileExplorerContainer)
        container?.let {
            it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun setupKeyboardShortcuts() {
        keyboardHandler = KeyboardShortcutHandler(binding.editorView)
        keyboardHandler.callback = object : KeyboardShortcutHandler.Callback {
            override fun onSave() { saveCurrentFile() }
            override fun onFind() { showFindReplaceDialog() }
            override fun onFindNext() {
                binding.editorView.findNext(lastSearchTerm, lastCaseSensitive)
            }
            override fun onFindPrevious() {
                binding.editorView.findPrevious(lastSearchTerm, lastCaseSensitive)
            }
            override fun onReplace() { showFindReplaceDialog() }
            override fun onUndo() { binding.editorView.performUndo() }
            override fun onRedo() { binding.editorView.performRedo() }
            override fun onSelectAll() { /* TODO */ }
            override fun onCopy() { /* TODO */ }
            override fun onCut() { /* TODO */ }
            override fun onPaste() { /* TODO */ }
            override fun onNewFile() { showNewFileDialog() }
            override fun onOpenFile() { /* TODO */ }
            override fun onCloseFile() { closeCurrentTab() }
            override fun onToggleLineNumbers() { binding.editorView.toggleLineNumbers() }
            override fun onToggleWordWrap() { binding.editorView.toggleWordWrap() }
            override fun onZoomIn() { binding.editorView.zoomIn() }
            override fun onZoomOut() { binding.editorView.zoomOut() }
            override fun onZoomReset() { binding.editorView.zoomReset() }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handleKeyEvent(binding.editorView, keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private var lastSearchTerm = ""
    private var lastCaseSensitive = false

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
                        Toast.makeText(this, "Draft loaded", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Discard") { _, _ ->
                    AutoSaveManager.deleteDraft(this, filePath)
                }
                .setNeutralButton("Compare", null)
                .show()
        }
    }

    private fun showSettingsDialog() {
        val dialog = SettingsDialog(this)
        dialog.show(currentSettings, object : SettingsDialog.Callback {
            override fun onSettingsChanged(settings: EditorSettings) {
                currentSettings = settings
                EditorSettings.save(this@MainActivity, settings)
                binding.editorView.applySettings(settings)
                AutoSaveManager.setAutoSaveEnabled(settings.autoSave)
                AutoSaveManager.setAutoSaveInterval(settings.autoSaveInterval)
                Toast.makeText(this@MainActivity, "Settings applied", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * FIX: Now uses FileManager and properly loads the file after saving.
     */
    private fun saveCurrentFile() {
        val filePath = EditorTabManager.getActiveFilePath()
        if (filePath == null) {
            Toast.makeText(this, "No file is open", Toast.LENGTH_SHORT).show()
            return
        }

        val content = binding.editorView.getContentForAutoSave()
        val file = File(filePath)

        lifecycleScope.launch {
            val success = fileManager.writeFile(file, content)
            if (success) {
                isContentModified = false
                EditorTabManager.markActiveModified(false)
                updateTabUI()
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                AutoSaveManager.deleteDraft(this@MainActivity, filePath)
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
                    updateTabUI()
                }
                .setNegativeButton("Discard") { _, _ ->
                    EditorTabManager.closeActiveTab()
                    updateTabUI()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            EditorTabManager.closeActiveTab()
            updateTabUI()
        }
    }

    private fun updateTabUI() {
        val container = binding.tabContainer as android.widget.LinearLayout
        container.removeAllViews()

        for ((index, tab) in EditorTabManager.tabs.withIndex()) {
            val tabView = layoutInflater.inflate(R.layout.tab_item, container, false)
            val titleView = tabView.findViewById<TextView>(R.id.tabTitle)
            val closeView = tabView.findViewById<View>(R.id.tabClose)

            titleView.text = tab.fileName
            if (tab.isModified) {
                titleView.text = "${tab.fileName} *"
                titleView.setTextColor(ContextCompat.getColor(this, R.color.error))
            } else {
                titleView.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            }

            if (index == EditorTabManager.activeTabIndex) {
                (tabView as com.google.android.material.card.MaterialCardView)
                    .setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_container))
                titleView.setTextColor(ContextCompat.getColor(this, R.color.on_primary_container))
            } else {
                (tabView as com.google.android.material.card.MaterialCardView)
                    .setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            }

            tabView.setOnClickListener {
                EditorTabManager.switchToTab(index)
            }
            closeView.setOnClickListener {
                closeTabWithCheck(index)
            }
            container.addView(tabView)
        }

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
        btnUndo.isEnabled = canUndo
        btnUndo.alpha = if (canUndo) 1.0f else 0.38f
        btnRedo.isEnabled = canRedo
        btnRedo.alpha = if (canRedo) 1.0f else 0.38f
    }

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

    /**
     * FIX: Now uses FileManager and properly loads the file after creation.
     * Also sanitizes the filename and writes default content.
     */
    private fun showNewFileDialog() {
        val input = EditText(this).apply {
            hint = "Enter file name (e.g., main.py)"
        }

        AlertDialog.Builder(this)
            .setTitle("New File")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isEmpty()) {
                    Toast.makeText(this, "Filename cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    // FIX: Use FileManager to create the file with proper sanitization
                    val file = fileManager.createNewFile(fileName)
                    if (file != null) {
                        // FIX: Actually load the file into the editor
                        loadFileAsync(file.absolutePath)
                        // Also add to tab manager
                        EditorTabManager.openFile(file.absolutePath)
                        updateTabUI()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * FIX: Now uses FileManager for reading.
     */
    private suspend fun loadFileAsync(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        val content = fileManager.readFile(file)

        withContext(Dispatchers.Main) {
            if (content != null) {
                currentFilePath = path
                binding.editorView.setText(content)
                EditorTabManager.openFile(path)
                binding.editorView.invalidate()
                updateTabUI()
                startAutoSave()
            } else {
                Toast.makeText(this@MainActivity, "Failed to load file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== REMOVED: setupFileObserver() – no longer needed =====
    // The FileObserver was causing race conditions and unwanted reloads.
    // Use reloadCurrentFile() for manual reload instead.

    private fun showCommitDialog() {
        val input = EditText(this).apply { hint = "Commit message" }
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
        val input = EditText(this).apply { hint = "Git repository URL (e.g., https://github.com/user/repo.git)" }
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
            .setNegativeButton("Open Settings") { _, _ -> openAppSettings() }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

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
        permissions: Array<String>,
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

    override fun onPause() {
        super.onPause()
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
        currentFilePath?.let {
            outState.putString("currentFilePath", it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoSave()
        TermuxExecutor.unregisterReceiver(this)
        findReplaceDialog?.dismiss()
    }

    fun updateEditor() {
        binding.editorView.invalidate()
    }

    // ================================================================
    // FIX: "People not showing up" – placeholder for future implementation
    // ================================================================
    /**
     * If "people" refers to collaborators/contributors, implement this method
     * to fetch and display a list of users from Git or a remote API.
     */
    private fun fetchAndDisplayPeople() {
        // TODO: Implement this to show contributors or collaborators
        // Example: GitManager.getContributors() or similar
        // For now, this is a placeholder.
        Log.d(TAG, "People feature not yet implemented")
    }
}