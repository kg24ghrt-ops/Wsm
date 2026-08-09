package com.example.personalcustomide

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object GitManager {
    private val _status = MutableStateFlow("Ready")
    val status = _status.asStateFlow()

    private var currentCommandId = 0

    fun gitStatus(context: Context, workingDir: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(context, id, workingDir, "git", "status", "--porcelain")
    }

    fun gitCommit(context: Context, workingDir: String, message: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(context, id, workingDir, "git", "commit", "-m", message)
    }

    fun gitPush(context: Context, workingDir: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(context, id, workingDir, "git", "push")
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

    fun gitInit(context: Context, workingDir: String) {
        val id = ++currentCommandId
        TermuxExecutor.executeCommand(context, id, workingDir, "git", "init")
    }

    fun gitAdd(context: Context, workingDir: String, vararg files: String) {
        val id = ++currentCommandId
        val args = mutableListOf("add")
        args.addAll(files)
        TermuxExecutor.executeCommand(context, id, workingDir, "git", *args.toTypedArray())
    }

    // Create a Python project: generate main.py and requirements.txt
    fun createPythonProject(context: Context, projectDir: String) {
        val id = ++currentCommandId
        // We'll use shell commands to create files
        val script = """
            mkdir -p "$projectDir"
            cat > "$projectDir/main.py" << 'EOF'
#!/usr/bin/env python3
print("Hello from your custom IDE!")
def main():
    print("Python project is ready.")
if __name__ == "__main__":
    main()
EOF
            cat > "$projectDir/requirements.txt" << 'EOF'
# Add your dependencies here
EOF
            echo "Python project created in $projectDir"
        """.trimIndent()
        TermuxExecutor.executeShellCommand(context, id, "/", script)
    }

    fun processResult(commandId: Int, result: TermuxExecutor.CommandResult) {
        val output = if (result.stdout.isNotEmpty()) result.stdout else result.stderr
        _status.update { "$output (exit: ${result.exitCode})" }
    }
}