package git

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LfsResolverTest {

    private val resolver = LfsResolver(FakeGitCommandRunner())

    // ── isLfsPointer ──────────────────────────────────────────────────────────

    @Test fun `isLfsPointer true for valid LFS pointer`() {
        val pointer = "version https://git-lfs.github.com/spec/v1\noid sha256:${"a".repeat(64)}\nsize 1234\n"
        assertTrue(resolver.isLfsPointer(pointer.toByteArray()))
    }

    @Test fun `isLfsPointer false for PNG header`() {
        assertFalse(resolver.isLfsPointer(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test fun `isLfsPointer false for empty bytes`() {
        assertFalse(resolver.isLfsPointer(ByteArray(0)))
    }

    @Test fun `isLfsPointer false for partial prefix`() {
        assertFalse(resolver.isLfsPointer("version https://git-lfs".toByteArray()))
    }

    // ── extractLfsOid ─────────────────────────────────────────────────────────

    @Test fun `extractLfsOid parses valid 64-char sha256`() {
        val oid = "b".repeat(64)
        val text = "version https://git-lfs.github.com/spec/v1\noid sha256:$oid\nsize 512\n"
        assertEquals(oid, resolver.extractLfsOid(text))
    }

    @Test fun `extractLfsOid null when line is absent`() {
        assertNull(resolver.extractLfsOid("version https://git-lfs.github.com/spec/v1\nsize 512\n"))
    }

    @Test fun `extractLfsOid null for wrong length`() {
        assertNull(resolver.extractLfsOid("oid sha256:tooshort"))
    }

    @Test fun `extractLfsOid trims whitespace`() {
        val oid = "c".repeat(64)
        assertNotNull(resolver.extractLfsOid("oid sha256:$oid  \n"))
    }

    // ── resolveGitBlobBytes pass-through ──────────────────────────────────────

    @Test fun `resolveGitBlobBytes returns raw bytes when not LFS pointer`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val result = resolver.resolveGitBlobBytes(
            java.io.File("."), "repo", "head", "img.png", png, mutableSetOf()
        )
        assertTrue(result.contentEquals(png))
    }
}

