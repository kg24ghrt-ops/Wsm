package com.example.personalcustomide

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object TermuxExecutor {
    private const val TAG = "TermuxExecutor"
    private const val ACTION_RESULT = "com.example.personalcustomide.TERMUX_RESULT"
    private const val EXTRA_COMMAND_ID = "command_id"

    // Termux constants
    private const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    // Intent extras - using the official keys
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"           // String
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"         // String[]
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"             // String
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"       // boolean
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT" // PendingIntent

    // Result extras delivered via the PendingIntent
    private const val EXTRA_RESULT_BUNDLE = "com.termux.RUN_COMMAND_RESULT"
    private const val EXTRA_STDOUT = "com.termux.RUN_COMMAND_STDOUT"
    private const val EXTRA_STDERR = "com.termux.RUN_COMMAND_STDERR"
    private const val EXTRA_EXIT_CODE = "com.termux.RUN_COMMAND_EXIT_CODE"

    // Permission required
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    private val resultChannel = Channel<Pair<Int, CommandResult>>(Channel.BUFFERED)
    val results: Flow<Pair<Int, CommandResult>> = resultChannel.receiveAsFlow()

    private var receiver: BroadcastReceiver? = null

    data class CommandResult(val stdout: String, val stderr: String, val exitCode: Int)

    /**
     * Check if the required RUN_COMMAND permission is granted.
     */
    fun hasRunCommandPermission(context: Context): Boolean {
        return context.checkSelfPermission(PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if Termux is installed.
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun registerReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_RESULT) return
                val commandId = intent.getIntExtra(EXTRA_COMMAND_ID, -1)

                // Extract the result bundle from the PendingIntent
                val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE)
                if (bundle == null) {
                    Log.w(TAG, "No result bundle for command $commandId")
                    resultChannel.trySend(commandId to CommandResult("", "No result bundle", -1))
                    return
                }

                val stdout = bundle.getString(EXTRA_STDOUT) ?: ""
                val stderr = bundle.getString(EXTRA_STDERR) ?: ""
                val exitCode = bundle.getInt(EXTRA_EXIT_CODE, -1)

                Log.d(TAG, "Command $commandId finished with exit code $exitCode")
                resultChannel.trySend(commandId to CommandResult(stdout, stderr, exitCode))
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, IntentFilter(ACTION_RESULT))
        this.receiver = receiver
    }

    fun unregisterReceiver(context: Context) {
        receiver?.let { LocalBroadcastManager.getInstance(context).unregisterReceiver(it) }
        receiver = null
    }

    /**
     * Executes a shell command via Termux's RunCommandService.
     *
     * @param context           Application context
     * @param commandId         Unique ID to match this command in the result flow
     * @param workingDirectory  Working directory (absolute path)
     * @param executable        The executable to run (e.g., "bash", "git", "/system/bin/sh")
     * @param arguments         Arguments to pass to the executable
     */
    fun executeCommand(
        context: Context,
        commandId: Int,
        workingDirectory: String,
        executable: String,
        vararg arguments: String
    ) {
        // Check permission first
        if (!hasRunCommandPermission(context)) {
            Log.e(TAG, "RUN_COMMAND permission not granted")
            resultChannel.trySend(commandId to CommandResult("", "Permission denied", -1))
            return
        }

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = ACTION_RUN_COMMAND

            putExtra(EXTRA_COMMAND_PATH, executable)
            putExtra(EXTRA_ARGUMENTS, arguments)
            putExtra(EXTRA_WORKDIR, workingDirectory)
            putExtra(EXTRA_BACKGROUND, true)

            val resultIntent = Intent(ACTION_RESULT).apply {
                putExtra(EXTRA_COMMAND_ID, commandId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                commandId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        try {
            context.startService(intent)
            Log.d(TAG, "Command $commandId sent to Termux: $executable ${arguments.joinToString(" ")}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Termux service", e)
            resultChannel.trySend(commandId to CommandResult("", "Service start failed: ${e.message}", -1))
        }
    }

    /**
     * Convenience method for running a shell command via "/system/bin/sh -c".
     */
    fun executeShellCommand(
        context: Context,
        commandId: Int,
        workingDirectory: String,
        command: String
    ) {
        executeCommand(
            context,
            commandId,
            workingDirectory,
            "/system/bin/sh",
            "-c", command
        )
    }
}