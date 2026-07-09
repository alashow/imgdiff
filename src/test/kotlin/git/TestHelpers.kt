package git

import java.io.File

/** Fake runner that returns pre-configured responses keyed by command list. */
class FakeGitCommandRunner(
    private val responses: Map<List<String>, GitCommandResult> = emptyMap()
) : GitCommandRunner {
    val invocations = mutableListOf<List<String>>()

    override fun run(repoDir: File, command: List<String>, timeoutSeconds: Long): GitCommandResult {
        invocations += command
        return responses[command] ?: GitCommandResult.EMPTY
    }

    override fun runWithInput(repoDir: File, command: List<String>, stdinBytes: ByteArray): GitCommandResult {
        invocations += command
        return responses[command] ?: GitCommandResult.EMPTY
    }
}

fun fakeRunner(vararg pairs: Pair<List<String>, GitCommandResult>) =
    FakeGitCommandRunner(mapOf(*pairs))

fun cmdResult(stdout: String) =
    GitCommandResult(0, stdout, stdout.toByteArray(Charsets.UTF_8))

