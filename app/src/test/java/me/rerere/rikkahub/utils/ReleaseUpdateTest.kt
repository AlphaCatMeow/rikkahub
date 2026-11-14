package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateTest {
    @Test
    fun mapsForkReleaseToUpdateAndOnlyOffersApks() {
        val info = decodeReleaseUpdate(
            """
            {
              "tag_name": "fork-v3.2.0",
              "published_at": "2026-11-13T10:00:00Z",
              "body": "Fork release notes",
              "html_url": "https://github.com/AlphaCatMeow/rikkahub/releases/tag/fork-v3.2.0",
              "assets": [
                {
                  "name": "app-github-arm64-v8a-release.apk",
                  "browser_download_url": "https://github.com/AlphaCatMeow/rikkahub/releases/download/fork-v3.2.0/app-github-arm64-v8a-release.apk",
                  "size": 60500000
                },
                {"name": "mapping.txt", "browser_download_url": "https://example.com/mapping.txt", "size": 10}
              ]
            }
            """.trimIndent()
        )

        assertEquals("3.2.0", info.version)
        assertTrue(Version(info.version) > Version("3.1.2"))
        assertEquals("2026-11-13T10:00:00Z", info.publishedAt)
        assertEquals("Fork release notes", info.changelog)
        assertEquals(1, info.downloads.size)
        assertTrue(info.downloads.single().url.startsWith("https://github.com/AlphaCatMeow/rikkahub/releases/download/"))
        assertEquals("60.5 MB", info.downloads.single().size)
    }

    @Test
    fun comparesForkRevisionsIncludingLegacyInstalledVersion() {
        val info = UpdateInfo("3.1.2-media.2", "", "", emptyList())

        assertTrue(info.isNewerThan("3.1.2"))
        assertTrue(info.isNewerThan("3.1.2-media.1"))
        assertEquals(false, info.isNewerThan("3.1.2-media.2"))
        assertEquals(false, info.isNewerThan("3.1.2-media.3"))
        assertEquals(false, info.isNewerThan("3.2.0"))
    }

    @Test
    fun supportsAbsentReleaseNotesAndAssets() {
        val info = decodeReleaseUpdate(
            """{"tag_name":"v3.1.2","published_at":"2026-11-13T10:00:00Z","body":null}"""
        )

        assertEquals("3.1.2", info.version)
        assertEquals("", info.changelog)
        assertTrue(info.downloads.isEmpty())
    }
}
