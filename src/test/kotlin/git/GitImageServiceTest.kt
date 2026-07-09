package git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class GitImageServiceTest {

    // Creates a real temp dir so GitImageService.getModifiedImages passes the isDirectory check.
    private fun tempDir(): File = Files.createTempDirectory("imgdiff-test").toFile()
        .also { it.deleteOnExit() }

    // ── getModifiedImages – extension filtering ────────────────────────────────

    @Test fun `filters out non-image files`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to
                cmdResult("M\timage.png\nM\tREADME.md\nM\tstyle.css\nM\tphoto.jpg\n"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(
            listOf(
                GitModifiedImage("image.png", "modified"),
                GitModifiedImage("photo.jpg", "modified")
            ),
            result
        )
    }

    @Test fun `accepts all supported image extensions`() {
        val dir = tempDir()
        val extensions = listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
        val status = extensions.joinToString("\n") { "M\tfile.$it" }
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult(status),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(extensions.map { GitModifiedImage("file.$it", "modified") }, result)
    }

    @Test fun `extension matching is case-insensitive`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult("M\tLogo.PNG\nM\tPhoto.JPG\n"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(
            listOf(
                GitModifiedImage("Logo.PNG", "modified"),
                GitModifiedImage("Photo.JPG", "modified")
            ),
            result
        )
    }

    // ── getModifiedImages – status line parsing ────────────────────────────────

    @Test fun `handles rename lines correctly`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to
                cmdResult("R100\told/icon.png\tnew/icon.png\n"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(listOf(GitModifiedImage("new/icon.png", "modified")), result)
    }

    @Test fun `handles added files`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult("A\tnew.png\n"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(listOf(GitModifiedImage("new.png", "new")), result)
    }

    @Test fun `includes untracked image files`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult(""),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("untracked.webp\nnotes.txt\n")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(listOf(GitModifiedImage("untracked.webp", "new")), result)
    }

    @Test fun `deduplicates entries across tracked and untracked`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult("M\timage.png\nA\timage.png\n"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("image.png\n")
        )
        val result = GitImageService(runner, LfsResolver(runner)).getModifiedImages(dir.absolutePath)
        assertEquals(1, result.size)
        assertEquals("image.png", result[0].path)
        assertEquals("new", result[0].changeType)
    }

    @Test fun `returns empty list for non-directory path`() {
        val result = GitImageService(FakeGitCommandRunner(), LfsResolver(FakeGitCommandRunner()))
            .getModifiedImages("/nonexistent/path/xyz")
        assertTrue(result.isEmpty())
    }

    @Test fun `returns empty list when git commands fail`() {
        val dir = tempDir()
        val result = GitImageService(FakeGitCommandRunner(), LfsResolver(FakeGitCommandRunner()))
            .getModifiedImages(dir.absolutePath)
        assertTrue(result.isEmpty())
    }

    // ── getModifiedImages – cache invalidation ────────────────────────────────

    @Test fun `caches repo state and invalidates on HEAD change`() {
        val dir = tempDir()
        val service = GitImageService(FakeGitCommandRunner(), LfsResolver(FakeGitCommandRunner()))

        // First call with HEAD=abc
        val r1 = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult("M\ta.png"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        GitImageService(r1, LfsResolver(r1)).getModifiedImages(dir.absolutePath)

        // Second call with different HEAD should not reuse stale state
        val r2 = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("def"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult("M\tb.png"),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result2 = GitImageService(r2, LfsResolver(r2)).getModifiedImages(dir.absolutePath)
        assertEquals(listOf(GitModifiedImage("b.png", "modified")), result2)
    }

    // ── getImageData – status codes ───────────────────────────────────────────

    @Test fun `returns invalid_repo for non-directory path`() {
        val data = GitImageService(FakeGitCommandRunner(), LfsResolver(FakeGitCommandRunner()))
            .getImageData("/nonexistent", "img.png")
        assertEquals("invalid_repo", data.beforeStatus)
    }

    @Test fun `returns missing_in_head when git show fails`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--", "img.png") to
                GitCommandResult.EMPTY
        )
        val service = GitImageService(runner, LfsResolver(runner))
        val data = service.getImageData(dir.absolutePath, "img.png")
        assertEquals("missing_in_head", data.beforeStatus)
    }

    @Test fun `afterBase64 is non-null when file exists on disk`() {
        val dir = tempDir()
        val imageFile = File(dir, "img.png").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
        val runner = fakeRunner(
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--", "img.png") to
                GitCommandResult.EMPTY
        )
        val service = GitImageService(runner, LfsResolver(runner))
        val data = service.getImageData(dir.absolutePath, "img.png")
        assertNotNull(data.afterBase64)
        imageFile.delete()
    }

    @Test fun `afterBase64 is null when file does not exist on disk`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--", "missing.png") to
                GitCommandResult.EMPTY
        )
        val data = GitImageService(runner, LfsResolver(runner))
            .getImageData(dir.absolutePath, "missing.png")
        assertEquals(null, data.afterBase64)
    }

    // ── startPrefetch ─────────────────────────────────────────────────────────

    @Test fun `startPrefetch returns false for invalid repo`() {
        val result = GitImageService(FakeGitCommandRunner(), LfsResolver(FakeGitCommandRunner()))
            .startPrefetch("/nonexistent")
        assertFalse(result)
    }

    @Test fun `startPrefetch returns true for valid directory`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult(""),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val result = GitImageService(runner, LfsResolver(runner)).startPrefetch(dir.absolutePath)
        assertTrue(result)
    }

    @Test fun `startPrefetch returns false when already in progress`() {
        val dir = tempDir()
        val runner = fakeRunner(
            listOf("git", "rev-parse", "HEAD") to cmdResult("abc123"),
            listOf("git", "diff", "--name-status", "--find-renames", "HEAD", "--") to cmdResult(""),
            listOf("git", "ls-files", "--others", "--exclude-standard") to cmdResult("")
        )
        val service = GitImageService(runner, LfsResolver(runner))
        service.startPrefetch(dir.absolutePath)
        val second = service.startPrefetch(dir.absolutePath)
        assertFalse(second)
    }
}

