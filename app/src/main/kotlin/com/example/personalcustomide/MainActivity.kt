package com.example.personalcustomide

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.personalcustomide.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomStatusText: TextView

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TERMUX_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bottomStatusText = binding.bottomStatusText

        // Check permissions
        checkPermissions()

        // Check Termux installation
        if (!TermuxExecutor.isTermuxInstalled(this)) {
            Toast.makeText(this, "Termux not installed. Please install from F-Droid.", Toast.LENGTH_LONG).show()
        }

        // Register Termux result receiver
        TermuxExecutor.registerReceiver(this)

        // Collect results
        lifecycleScope.launch {
            TermuxExecutor.results.collect { (commandId, result) ->
                runOnUiThread {
                    GitManager.processResult(commandId, result)
                    // Update bottom panel with latest status
                    bottomStatusText.text = GitManager.status.value
                }
            }
        }

        // Set up file explorer fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fileExplorerContainer, FileExplorerFragment())
                .commit()
        }

        // Editor view setup
        binding.editorView.setEditorNative(EditorNative)
        val defaultFile = filesDir.resolve("home/test.txt")
        if (defaultFile.exists()) {
            EditorNative.loadFile(defaultFile.absolutePath)
            binding.editorView.invalidate()
        } else {
            // Create default file
            defaultFile.parentFile?.mkdirs()
            defaultFile.writeText("Welcome to your IDE!\nEdit this file.")
            EditorNative.loadFile(defaultFile.absolutePath)
            binding.editorView.invalidate()
        }

        // Bottom panel buttons
        binding.btnGitStatus.setOnClickListener {
            val workingDir = filesDir.resolve("home").absolutePath
            GitManager.gitStatus(this, workingDir)
        }
        binding.btnGitCommit.setOnClickListener {
            showCommitDialog()
        }
        binding.btnGitPush.setOnClickListener {
            val workingDir = filesDir.resolve("home").absolutePath
            GitManager.gitPush(this, workingDir)
        }
        binding.btnCloneRepo.setOnClickListener {
            showCloneDialog()
        }
        binding.btnCreatePython.setOnClickListener {
            val workingDir = filesDir.resolve("home").absolutePath
            GitManager.createPythonProject(this, "$workingDir/my_python_project")
        }

        // Initial Git status
        val workingDir = filesDir.resolve("home").absolutePath
        GitManager.gitStatus(this, workingDir)
    }

    private fun showCommitDialog() {
        val input = EditText(this)
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
        val input = EditText(this)
        input.hint = "Git repository URL (e.g., https://github.com/user/repo.git)"
        AlertDialog.Builder(this)
            .setTitle("Clone Repository")
            .setView(input)
            .setPositiveButton("Clone") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    val workingDir = filesDir.resolve("home").absolutePath
                    // Extract repo name from URL
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

    private fun checkPermissions() {
        // Storage permissions
        val storagePerms = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val missingStorage = storagePerms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingStorage.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingStorage.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        // Termux RUN_COMMAND permission
        if (!TermuxExecutor.hasRunCommandPermission(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(TermuxExecutor.PERMISSION_RUN_COMMAND),
                TERMUX_PERMISSION_REQUEST_CODE
            )
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
                    Toast.makeText(this, "RUN_COMMAND permission required", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TermuxExecutor.unregisterReceiver(this)
    }
}