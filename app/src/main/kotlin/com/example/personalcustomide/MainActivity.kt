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

        val defaultFile = filesDir.resolve("home/test.txt")
        val fileToLoad = currentFilePath ?: defaultFile.absolutePath
        // FIXED: fileToLoad is now non-nullable String
        lifecycleScope.launch { loadFileAsync(fileToLoad) }

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

        val workingDir = filesDir.resolve("home").absolutePath
        GitManager.gitStatus(this, workingDir)
        setupFileObserver(fileToLoad)
    }

    private suspend fun loadFileAsync(path: String) = withContext(Dispatchers.IO) {
        val success = EditorNative.loadFile(path)
        withContext(Dispatchers.Main) {
            if (success) {
                currentFilePath = path
                binding.editorView.invalidate()
                setupFileObserver(path)
            } else {
                Toast.makeText(this@MainActivity, "Failed to load file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFileObserver(path: String) {
        fileObserver?.stopWatching()
        fileObserver = object : FileObserver(path) {
            override fun onEvent(event: Int, path: String?) {
                if (event == MODIFY || event == CLOSE_WRITE) {
                    runOnUiThread {
                        lifecycleScope.launch {
                            // FIXED: path is nullable, use safe call
                            path?.let { loadFileAsync(it) }
                        }
                    }
                }
            }
        }
        fileObserver?.startWatching()
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

    fun updateEditor() {
        binding.editorView.invalidate()
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentFilePath?.let { outState.putString("currentFilePath", it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        TermuxExecutor.unregisterReceiver(this)
    }
}