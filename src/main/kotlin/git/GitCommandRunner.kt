package git

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

interface GitCommandRunner {
    fun run(repoDir: File, command: List<String>, timeoutSeconds: Long = 20): GitCommandResult
    fun runWithInput(repoDir: File, command: List<String>, stdinBytes: ByteArray): GitCommandResult
}

class ProcessGitCommandRunner : GitCommandRunner {
    private val shellEnvironment: Map<String, String> = resolveShellEnvironment()
    private val resolvedGitCommand: String = resolveGitExecutable()
    private val resolvedPathEnv: String? = resolvePathEnv()
    private val resolvedSshAuthSock: String? = resolveSshAuthSock()

    override fun run(repoDir: File, command: List<String>, timeoutSeconds: Long): GitCommandResult {
        return try {
            val process = ProcessBuilder(normalizeCommand(command))
                .directory(repoDir)
                .apply { applyGitEnvironment(environment()) }
                .start()
            processToResult(process, timeoutSeconds)
        } catch (_: Exception) {
            GitCommandResult.EMPTY
        }
    }

    override fun runWithInput(repoDir: File, command: List<String>, stdinBytes: ByteArray): GitCommandResult {
        return try {
            val process = ProcessBuilder(normalizeCommand(command))
                .directory(repoDir)
                .apply { applyGitEnvironment(environment()) }
                .start()
            process.outputStream.use { it.write(stdinBytes) }
            processToResult(process, 20)
        } catch (_: Exception) {
            GitCommandResult.EMPTY
        }
    }

    private fun applyGitEnvironment(env: MutableMap<String, String>) {
        applyPreferredShellEnvironment(env)
        env["GIT_TERMINAL_PROMPT"] = "0"
        resolvedPathEnv?.takeIf { it.isNotBlank() }?.let { env["PATH"] = it }
        resolvedSshAuthSock?.takeIf { it.isNotBlank() }?.let { env["SSH_AUTH_SOCK"] = it }
    }

    private fun applyPreferredShellEnvironment(env: MutableMap<String, String>) {
        if (shellEnvironment.isEmpty()) return

        // Finder-launched macOS apps miss login-shell env, which can break git-lfs auth helpers.
        shellEnvironment.forEach { (key, value) ->
            if (value.isNotBlank()) {
                env[key] = value
            }
        }
    }

    private fun processToResult(process: Process, timeoutSeconds: Long): GitCommandResult {
        val stdoutRef = AtomicReference(ByteArray(0))
        val stderrRef = AtomicReference(ByteArray(0))

        val stdoutReader = thread(start = true, isDaemon = true) {
            stdoutRef.set(runCatching { process.inputStream.readBytes() }.getOrElse { ByteArray(0) })
        }
        val stderrReader = thread(start = true, isDaemon = true) {
            stderrRef.set(runCatching { process.errorStream.readBytes() }.getOrElse { ByteArray(0) })
        }

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            stdoutReader.join(250)
            stderrReader.join(250)
            return GitCommandResult.EMPTY
        }

        stdoutReader.join(500)
        stderrReader.join(500)

        val stdoutBytes = stdoutRef.get()
        val stderrBytes = stderrRef.get()
        val stdoutText = if (stdoutBytes.isNotEmpty()) {
            stdoutBytes.toString(Charsets.UTF_8)
        } else {
            stderrBytes.toString(Charsets.UTF_8)
        }

        return GitCommandResult(process.exitValue(), stdoutText, stdoutBytes)
    }

    private fun normalizeCommand(command: List<String>): List<String> {
        if (command.isEmpty() || command.first() != "git") return command
        return buildList {
            add(resolvedGitCommand)
            // Avoid failing on repos flagged as dubious ownership in packaged app launches.
            add("-c")
            add("safe.directory=*")
            addAll(command.drop(1))
        }
    }

    private fun resolvePathEnv(): String? {
        val shellPath = shellEnvironment["PATH"]?.trim().orEmpty()
        if (shellPath.isNotEmpty()) return shellPath

        val current = System.getenv("PATH")?.trim().orEmpty()
        if (current.isNotEmpty()) return current

        if (!isMacOs()) return null
        return runShellCapture("echo -n \"\$PATH\"", timeoutSeconds = 2)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun resolveSshAuthSock(): String? {
        val shellSock = shellEnvironment["SSH_AUTH_SOCK"]?.trim().orEmpty()
        if (shellSock.isNotEmpty()) return shellSock

        val current = System.getenv("SSH_AUTH_SOCK")?.trim().orEmpty()
        if (current.isNotEmpty()) return current

        if (!isMacOs()) return null
        return runCommandCapture(listOf("/bin/launchctl", "getenv", "SSH_AUTH_SOCK"), timeoutSeconds = 2)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun runCommandCapture(command: List<String>, timeoutSeconds: Long): String? {
        return runCatching {
            val process = ProcessBuilder(command).start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            process.inputStream.readBytes().toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun resolveShellEnvironment(): Map<String, String> {
        if (!isMacOs()) return emptyMap()

        val raw = runShellCapture("env -0", timeoutSeconds = 2)
            ?.trimEnd('\u0000')
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyMap()

        return raw
            .split('\u0000')
            .asSequence()
            .mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                val key = line.substring(0, separatorIndex)
                val value = line.substring(separatorIndex + 1)
                key to value
            }
            .toMap()
    }

    private fun isMacOs(): Boolean = System.getProperty("os.name", "").lowercase().contains("mac")

    private fun runShellCapture(command: String, timeoutSeconds: Long): String? {
        for (shell in shellCandidates()) {
            val output = runCommandCapture(listOf(shell, "-lc", command), timeoutSeconds)
            if (output != null) return output
        }
        return null
    }

    private fun shellCandidates(): List<String> {
        val candidates = buildList {
            System.getenv("IMAGEDIFF_SHELL")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            System.getenv("SHELL")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            add("/bin/zsh")
            add("/bin/bash")
            add("/bin/sh")
        }

        return candidates.distinct().filter { File(it).exists() && File(it).canExecute() }
    }

    private fun resolveGitExecutable(): String {
        val envOverride = System.getenv("IMAGEDIFF_GIT_PATH")?.trim().orEmpty()
        if (envOverride.isNotEmpty()) {
            val candidate = File(envOverride)
            if (candidate.exists() && candidate.canExecute()) return candidate.absolutePath
        }

        val os = System.getProperty("os.name", "").lowercase()
        val candidates = when {
            os.contains("mac") -> listOf(
                "/opt/homebrew/bin/git", // Apple Silicon Homebrew
                "/usr/local/bin/git",    // Intel Homebrew
                "/usr/bin/git"           // Xcode/system git
            )
            os.contains("win") -> listOf(
                "C:/Program Files/Git/bin/git.exe",
                "C:/Program Files/Git/cmd/git.exe"
            )
            else -> listOf(
                "/usr/bin/git",
                "/usr/local/bin/git"
            )
        }

        return candidates.firstOrNull { path ->
            val file = File(path)
            file.exists() && file.canExecute()
        } ?: "git"
    }
}
