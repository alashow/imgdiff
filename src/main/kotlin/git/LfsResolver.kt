package git

import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val LFS_POINTER_PREFIX = "version https://git-lfs.github.com/spec/v1"
private const val LFS_FETCH_TIMEOUT_SECONDS = 8L

class LfsResolver(private val runner: GitCommandRunner) {

    private val gitDirCache = ConcurrentHashMap<String, String>()

    fun isLfsPointer(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val preview = bytes.copyOfRange(0, minOf(bytes.size, LFS_POINTER_PREFIX.length + 16))
            .toString(Charsets.UTF_8)
        return preview.startsWith(LFS_POINTER_PREFIX)
    }

    fun extractLfsOid(pointerText: String): String? =
        pointerText
            .lineSequence()
            .firstOrNull { it.startsWith("oid sha256:") }
            ?.substringAfter("oid sha256:")
            ?.trim()
            ?.takeIf { it.length == 64 }

    fun extractLfsOidFromTree(repoDir: File, pathInHead: String): String? {
        val blobRef = runner.run(repoDir, listOf("git", "rev-parse", "HEAD:$pathInHead"))
            .takeIf { it.exitCode == 0 }?.stdout?.trim().orEmpty()
        if (blobRef.isEmpty()) return null
        val pointer = runner.run(repoDir, listOf("git", "cat-file", "-p", blobRef))
            .takeIf { it.exitCode == 0 }?.stdout.orEmpty()
        return if (pointer.isEmpty()) null else extractLfsOid(pointer)
    }

    fun resolveLocalLfsObject(repoDir: File, oid: String): ByteArray? {
        val gitDir = gitDirCache.computeIfAbsent(repoDir.absolutePath) {
            val resolved = runner.run(repoDir, listOf("git", "rev-parse", "--git-dir"))
                .takeIf { it.exitCode == 0 }?.stdout?.trim().orEmpty()
            if (resolved.isNotEmpty()) File(repoDir, resolved).absolutePath else ""
        }
        if (gitDir.isEmpty()) return null
        val objectFile = File(gitDir, "lfs/objects/${oid.substring(0, 2)}/${oid.substring(2, 4)}/$oid")
        if (!objectFile.exists() || !objectFile.isFile || objectFile.length() == 0L) return null
        return try {
            val bytes = objectFile.readBytes()
            if (isLfsPointer(bytes)) null else bytes
        } catch (_: Exception) {
            null
        }
    }

    fun resolveGitBlobBytes(
        repoDir: File,
        repoPath: String,
        headRef: String,
        pathInHead: String,
        rawBlob: ByteArray,
        lfsFetchAttempted: MutableSet<String>
    ): ByteArray {
        if (!isLfsPointer(rawBlob)) return rawBlob

        val pointerText = rawBlob.toString(Charsets.UTF_8)
        val pointerOid = extractLfsOid(pointerText) ?: extractLfsOidFromTree(repoDir, pathInHead)
        pointerOid?.let { oid -> resolveLocalLfsObject(repoDir, oid)?.let { return it } }

        val smudged = runner.runWithInput(repoDir, listOf("git", "lfs", "smudge"), rawBlob)
        if (smudged.exitCode == 0 && smudged.stdoutBytes.isNotEmpty() && !isLfsPointer(smudged.stdoutBytes)) {
            return smudged.stdoutBytes
        }

        val fetchKey = "$repoPath|$headRef|$pathInHead"
        if (lfsFetchAttempted.add(fetchKey)) {
            runner.run(
                repoDir,
                listOf("git", "lfs", "fetch", "origin", "HEAD", "--include=$pathInHead", "--exclude="),
                timeoutSeconds = LFS_FETCH_TIMEOUT_SECONDS
            )
        }

        pointerOid?.let { oid -> resolveLocalLfsObject(repoDir, oid)?.let { return it } }

        val filteredRetry = runner.run(repoDir, listOf("git", "cat-file", "--filters", "HEAD:$pathInHead"), timeoutSeconds = 10)
        if (filteredRetry.exitCode == 0 && filteredRetry.stdoutBytes.isNotEmpty() && !isLfsPointer(filteredRetry.stdoutBytes)) {
            return filteredRetry.stdoutBytes
        }

        val smudgeRetry = runner.runWithInput(repoDir, listOf("git", "lfs", "smudge"), rawBlob)
        if (smudgeRetry.exitCode == 0 && smudgeRetry.stdoutBytes.isNotEmpty() && !isLfsPointer(smudgeRetry.stdoutBytes)) {
            return smudgeRetry.stdoutBytes
        }

        return ByteArray(0)
    }

    internal fun clearGitDirCache() = gitDirCache.clear()
}
