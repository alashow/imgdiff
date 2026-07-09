package git

internal data class RepoState(
    val headRef: String,
    val previousPathByCurrentPath: Map<String, String>
)

data class GitImageDiffData(
    val beforeBase64: String?,
    val afterBase64: String?,
    val beforeStatus: String
)

data class GitModifiedImage(
    val path: String,
    val changeType: String
)

data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stdoutBytes: ByteArray
) {
    companion object {
        val EMPTY = GitCommandResult(1, "", ByteArray(0))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GitCommandResult) return false
        return exitCode == other.exitCode &&
            stdout == other.stdout &&
            stdoutBytes.contentEquals(other.stdoutBytes)
    }

    override fun hashCode(): Int {
        var result = exitCode
        result = 31 * result + stdout.hashCode()
        result = 31 * result + stdoutBytes.contentHashCode()
        return result
    }
}

