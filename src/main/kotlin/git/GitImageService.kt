package git

import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val PREFETCH_LIMIT = 120
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

class GitImageService(
    private val runner: GitCommandRunner = ProcessGitCommandRunner(),
    private val lfsResolver: LfsResolver = LfsResolver(runner)
) {
    private val repoStateCache = ConcurrentHashMap<String, RepoState>()
    private val gitImageDataCache = ConcurrentHashMap<String, GitImageDiffData>()
    private val lfsFetchAttempted: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val prefetchInProgress: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun getModifiedImages(repoPath: String): List<GitModifiedImage> {
        val repoDir = File(repoPath)
        if (!repoDir.isDirectory) return emptyList()

        val headRef = runner.run(repoDir, listOf("git", "rev-parse", "HEAD"))
            .takeIf { it.exitCode == 0 }?.stdout?.trim().orEmpty()

        val previousPathByCurrentPath = mutableMapOf<String, String>()

        val diffResult = runner.run(repoDir, listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--"))
        val trackedChanges = if (diffResult.exitCode == 0) {
            parseTrackedFromNameStatus(diffResult.stdout.lines().filter { it.isNotBlank() }, previousPathByCurrentPath)
        } else {
            // Fallback for repos where diff against HEAD fails in packaged runtime.
            val statusResult = runner.run(repoDir, listOf("git", "status", "--porcelain", "-uall"))
            parseTrackedFromPorcelain(statusResult.stdout.lines().filter { it.isNotBlank() }, previousPathByCurrentPath)
        }

        val untracked = runner.run(repoDir, listOf("git", "ls-files", "--others", "--exclude-standard"))
            .takeIf { it.exitCode == 0 }
            ?.stdout
            ?.lines()
            ?.filter { it.isNotBlank() }
            ?.map { GitModifiedImage(path = it, changeType = "new") }
            ?: emptyList()

        val deduped = linkedMapOf<String, GitModifiedImage>()
        (trackedChanges + untracked).forEach { entry ->
            val existing = deduped[entry.path]
            if (existing == null || entry.changeType == "new") {
                deduped[entry.path] = entry
            }
        }

        val filtered = deduped.values
            .filter { entry -> IMAGE_EXTENSIONS.contains(entry.path.substringAfterLast('.', "").lowercase()) }

        val previousState = repoStateCache[repoPath]
        repoStateCache[repoPath] = RepoState(headRef, previousPathByCurrentPath)

        if (previousState?.headRef != headRef) {
            gitImageDataCache.keys.removeIf { it.startsWith("$repoPath|") }
            lfsFetchAttempted.removeIf { it.startsWith("$repoPath|") }
        }

        return filtered
    }

    fun getImageData(repoPath: String, filePath: String): GitImageDiffData {
        val repoDir = File(repoPath)
        if (!repoDir.isDirectory) return GitImageDiffData(null, null, "invalid_repo")

        val repoState = repoStateCache[repoPath]
        val afterFile = File(repoPath, filePath)
        val afterStamp = if (afterFile.exists()) "${afterFile.length()}:${afterFile.lastModified()}" else "missing"
        val cacheKey = "$repoPath|${repoState?.headRef.orEmpty()}|$filePath|$afterStamp"
        gitImageDataCache[cacheKey]?.let { return it }

        val previousPath = repoState?.previousPathByCurrentPath?.get(filePath)
            ?: resolvePreviousPath(repoDir, filePath)

        val showResult = runner.run(repoDir, listOf("git", "show", "HEAD:$previousPath"))
        val previousExistsInHead = showResult.exitCode == 0
        val beforeBytes = if (previousExistsInHead && showResult.stdoutBytes.isNotEmpty()) {
            lfsResolver.resolveGitBlobBytes(
                repoDir, repoPath, repoState?.headRef.orEmpty(), previousPath, showResult.stdoutBytes, lfsFetchAttempted
            )
        } else {
            ByteArray(0)
        }

        val beforeBase64 = if (beforeBytes.isNotEmpty()) Base64.getEncoder().encodeToString(beforeBytes) else null
        val afterBase64 = if (afterFile.exists()) Base64.getEncoder().encodeToString(afterFile.readBytes()) else null
        val beforeStatus = when {
            beforeBytes.isNotEmpty() -> "ok"
            !previousExistsInHead -> "missing_in_head"
            else -> "lfs_unavailable"
        }

        val result = GitImageDiffData(beforeBase64, afterBase64, beforeStatus)
        if (gitImageDataCache.size > 800) gitImageDataCache.clear()
        gitImageDataCache[cacheKey] = result
        return result
    }

    fun startPrefetch(repoPath: String): Boolean {
        val repoDir = File(repoPath)
        if (!repoDir.isDirectory) return false
        if (!prefetchInProgress.add(repoPath)) return false

        thread(name = "imgdiff-prefetch", isDaemon = true) {
            try {
                getModifiedImages(repoPath).take(PREFETCH_LIMIT).forEach { image ->
                    try { getImageData(repoPath, image.path) } catch (_: Exception) {}
                }
            } finally {
                prefetchInProgress.remove(repoPath)
            }
        }
        return true
    }

    private fun parseTrackedFromNameStatus(
        statusLines: List<String>,
        previousPathByCurrentPath: MutableMap<String, String>
    ): List<GitModifiedImage> {
        val trackedChanges = mutableListOf<GitModifiedImage>()
        for (line in statusLines) {
            val parts = line.split('\t')
            if (parts.isEmpty()) continue
            when {
                parts[0].startsWith("R") && parts.size >= 3 -> {
                    previousPathByCurrentPath[parts[2]] = parts[1]
                    trackedChanges += GitModifiedImage(path = parts[2], changeType = "modified")
                }
                parts[0].startsWith("A") && parts.size >= 2 -> trackedChanges += GitModifiedImage(path = parts[1], changeType = "new")
                parts[0].startsWith("C") && parts.size >= 2 -> trackedChanges += GitModifiedImage(path = parts[1], changeType = "modified")
                parts[0].startsWith("M") && parts.size >= 2 -> trackedChanges += GitModifiedImage(path = parts[1], changeType = "modified")
            }
        }
        return trackedChanges
    }

    private fun parseTrackedFromPorcelain(
        lines: List<String>,
        previousPathByCurrentPath: MutableMap<String, String>
    ): List<GitModifiedImage> {
        val trackedChanges = mutableListOf<GitModifiedImage>()
        for (line in lines) {
            if (line.length < 4) continue
            val status = line.substring(0, 2)
            val payload = line.substring(3)
            if (status == "??") {
                trackedChanges += GitModifiedImage(path = payload, changeType = "new")
                continue
            }
            if (payload.contains(" -> ")) {
                val parts = payload.split(" -> ", limit = 2)
                if (parts.size == 2) {
                    previousPathByCurrentPath[parts[1]] = parts[0]
                    trackedChanges += GitModifiedImage(path = parts[1], changeType = "modified")
                    continue
                }
            }
            trackedChanges += GitModifiedImage(path = payload, changeType = "modified")
        }
        return trackedChanges
    }

    private fun resolvePreviousPath(repoDir: File, currentPath: String): String {
        val status = runner.run(
            repoDir,
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--", currentPath)
        )
        if (status.exitCode != 0) return currentPath
        val line = status.stdout.lineSequence().firstOrNull { it.isNotBlank() } ?: return currentPath
        val parts = line.split('\t')
        return if (parts.size >= 3 && parts[0].startsWith("R")) parts[1] else currentPath
    }
}
