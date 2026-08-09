package com.example.personalcustomide

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GitManager {
    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()

    private var currentCommandId = 0

    fun gitStatus(context: Context, workingDir: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(
            context,
            id,
            workingDir,
            "git",
            "status", "--porcelain"
        )
    }

    fun gitCommit(context: Context, workingDir: String, message: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(
            context,
            id,
            workingDir,
            "git",
            "commit", "-m", message
        )
    }

    fun gitPush(context: Context, workingDir: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(
            context,
            id,
            workingDir,
            "git",
            "push"
        )
    }

    fun gitClone(context: Context, workingDir: String, repoUrl: String, targetDir: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(
            context,
            id,
            workingDir,
            "git",
            "clone", repoUrl, targetDir
        )
    }

    fun gitAdd(context: Context, workingDir: String, vararg files: String) {
        val id = ++currentCommandId
        val args = mutableListOf("add")
        args.addAll(files)
        TermuxExecutor.executeCommand(
            context,
            id,
            workingDir,
            "git",
            *args.toTypedArray()
        )
    }

    /**
     * Process a command result. Call this from your result collector.
     */
    fun processResult(commandId: Int, result: TermuxExecutor.CommandResult) {
        val output = if (result.stdout.isNotEmpty()) result.stdout else result.stderr
        _status.update { "Exit code: ${result.exitCode}\n$output" }
    }
}