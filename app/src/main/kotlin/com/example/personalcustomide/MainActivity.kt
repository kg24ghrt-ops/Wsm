package com.example.personalcustomide

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.personalcustomide.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val TERMUX_PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                    val output = if (result.stdout.isNotEmpty()) result.stdout else result.stderr
                    Log.d("TermuxResult", "Command $commandId: exit=${result.exitCode}, output=$output")
                    // Update your UI here
                    Toast.makeText(
                        this@MainActivity,
                        "Command $commandId finished (exit: ${result.exitCode})",
                        Toast.LENGTH_SHORT
                    ).show()
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
        }

        // Test Git
        testGit()
    }

    private fun testGit() {
        if (TermuxExecutor.hasRunCommandPermission(this)) {
            val workingDir = filesDir.absolutePath
            TermuxExecutor.executeShellCommand(this, 999, workingDir, "git --version")
        } else {
            Toast.makeText(this, "RUN_COMMAND permission not granted", Toast.LENGTH_SHORT).show()
        }
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

        // Termux RUN_COMMAND permission[reference:24]
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
                    testGit()
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