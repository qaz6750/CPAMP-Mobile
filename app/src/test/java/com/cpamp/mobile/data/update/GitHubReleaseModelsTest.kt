package com.cpamp.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseModelsTest {
    private val apkUrl =
        "https://github.com/qaz6750/CPA-Manager-Plus-Android/releases/download/v1.0.4/cpamp-mobile-v1.0.4.apk"
    private val checksumUrl = "$apkUrl.sha256"

    @Test
    fun `compares semantic versions numerically`() {
        assertTrue(isNewerVersion("v1.0.10", "1.0.4"))
        assertTrue(isNewerVersion("1.1.0", "1.0.4"))
        assertFalse(isNewerVersion("1.0.4", "1.0.4"))
        assertFalse(isNewerVersion("preview", "1.0.4"))
    }

    @Test
    fun `selects only the fixed signed release asset contract`() {
        val release = GitHubReleaseDto(
            tagName = "v1.0.4",
            htmlUrl = "https://github.com/qaz6750/CPA-Manager-Plus-Android/releases/tag/v1.0.4",
            assets = listOf(
                GitHubReleaseAssetDto("cpamp-mobile-v1.0.4.apk", 10, apkUrl),
                GitHubReleaseAssetDto("cpamp-mobile-v1.0.4.apk.sha256", 64, checksumUrl),
            ),
        )

        assertEquals("1.0.4", requireNotNull(release.releaseAssets()).version)
        assertNull(release.copy(prerelease = true).releaseAssets())
        assertNull(release.copy(tagName = "1.0.4").releaseAssets())
        assertNull(release.copy(assets = release.assets.dropLast(1)).releaseAssets())
        assertNull(
            release.copy(
                assets = release.assets.map {
                    it.copy(downloadUrl = it.downloadUrl.replace("https://github.com/", "https://example.test/"))
                },
            ).releaseAssets(),
        )
        assertNull(
            release.copy(
                assets = release.assets.map { it.copy(downloadUrl = "${it.downloadUrl}?unexpected=true") },
            ).releaseAssets(),
        )
    }
}
