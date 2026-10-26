package dev.rubensalas.tinta.data.update

import dev.rubensalas.tinta.data.api.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReleaseTest {
    private val full = """
        {
          "tag_name": "v0.2.1",
          "name": "Tinta 0.2.1",
          "body": "- Se actualiza sola.\n- Arregla los gestos.",
          "draft": false,
          "assets": [
            {"name": "SHA256SUMS", "browser_download_url": "https://example.test/SHA256SUMS", "size": 90},
            {"name": "atalaya-desktop", "browser_download_url": "https://example.test/atalaya-desktop"},
            {"name": "tinta.apk", "browser_download_url": "https://example.test/tinta.apk", "size": 1234}
          ]
        }
    """.trimIndent()

    @Test
    fun readsTagNotesAndAssets() {
        val release = parseRelease(full)
        assertEquals("v0.2.1", release.tag)
        assertEquals(Version(0, 2, 1), release.version)
        assertEquals("- Se actualiza sola.\n- Arregla los gestos.", release.notes)
        assertEquals("https://example.test/tinta.apk", release.apkUrl)
        assertEquals("https://example.test/SHA256SUMS", release.sumsUrl)
    }

    @Test
    fun nullBodyMeansNoNotes() {
        val release = parseRelease(full.replace("\"body\": \"- Se actualiza sola.\\n- Arregla los gestos.\"", "\"body\": null"))
        assertEquals("", release.notes)
    }

    @Test
    fun missingApkOrSumsIsAnError() {
        val withoutApk = full.replace("\"tinta.apk\"", "\"otro.apk\"")
        val e = assertThrows(ApiException::class.java) { parseRelease(withoutApk) }
        assertTrue(e.message!!.contains("tinta.apk"))

        val withoutSums = full.replace("\"SHA256SUMS\"", "\"SUMAS\"")
        assertThrows(ApiException::class.java) { parseRelease(withoutSums) }
    }

    @Test
    fun badTagIsAnError() {
        val e = assertThrows(ApiException::class.java) { parseRelease(full.replace("v0.2.1", "nightly")) }
        assertTrue(e.message!!.contains("nightly"))
    }

    @Test
    fun brokenJsonIsAnError() {
        assertThrows(ApiException::class.java) { parseRelease("<html>no es json</html>") }
    }

    @Test
    fun findsHashOfFileInSha256Sums() {
        val sums = """
            ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789  atalaya-desktop
            0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef  tinta.apk
        """.trimIndent()
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            expectedSha256(sums, "tinta.apk"),
        )
        assertEquals(
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            expectedSha256(sums, "atalaya-desktop"),
        )
    }

    @Test
    fun binaryModeStarIsAccepted() {
        val sums = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef *tinta.apk\n"
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            expectedSha256(sums, "tinta.apk"),
        )
    }

    @Test
    fun missingFileOrBadLineGivesNull() {
        assertNull(expectedSha256("hex-no-valido  tinta.apk\n", "tinta.apk"))
        assertNull(expectedSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef  otro\n", "tinta.apk"))
    }

    @Test
    fun sha256OfKnownText() {
        val dir: File = Files.createTempDirectory("sha").toFile()
        try {
            val file = File(dir, "abc.txt").apply { writeText("abc") }
            // SHA-256 de «abc», del estándar FIPS 180-2.
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(file))
        } finally {
            dir.deleteRecursively()
        }
    }
}
