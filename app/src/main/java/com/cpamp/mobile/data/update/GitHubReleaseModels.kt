package com.cpamp.mobile.data.update

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
data class GitHubReleaseAssetDto(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

data class ReleaseAssets(
    val version: String,
    val apk: GitHubReleaseAssetDto,
    val checksum: GitHubReleaseAssetDto,
)

fun GitHubReleaseDto.releaseAssets(): ReleaseAssets? {
    if (draft || prerelease) return null
    val version = tagName.removePrefix("v")
    if (SemanticVersion.parse(version) == null || tagName != "v$version") return null
    val apkName = "cpamp-mobile-v$version.apk"
    val checksumName = "$apkName.sha256"
    val apk = assets.singleOrNull {
        it.name == apkName && it.downloadUrl.isTrustedReleaseAsset(tagName, apkName)
    } ?: return null
    val checksum = assets.singleOrNull {
        it.name == checksumName && it.downloadUrl.isTrustedReleaseAsset(tagName, checksumName)
    } ?: return null
    return ReleaseAssets(version, apk, checksum)
}

fun GitHubReleaseDto.displayBody(language: String): String? {
    val content = body
        ?.substringBefore("\n## Download / 下载")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val sections = LANGUAGE_SECTION.findAll(content).toList()
    val localizedContent = if (sections.isEmpty()) {
        content
    } else {
        val preferredHeading = if (language.equals("zh", ignoreCase = true)) "中文" else "English"
        val selectedIndex = sections.indexOfFirst { it.groupValues[1] == preferredHeading }
            .takeIf { it >= 0 }
            ?: 0
        val selected = sections[selectedIndex]
        val intro = content.substring(0, sections.first().range.first).trim()
        val sectionEnd = sections.getOrNull(selectedIndex + 1)?.range?.first ?: content.length
        listOf(intro, content.substring(selected.range.last + 1, sectionEnd).trim())
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
    }
    return localizedContent.lineSequence()
    ?.map { line -> line.replaceFirst(MARKDOWN_HEADING, "") }
    ?.joinToString("\n")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
}

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)

    companion object {
        fun parse(value: String): SemanticVersion? {
            val match = VERSION.matchEntire(value.trim().removePrefix("v")) ?: return null
            return SemanticVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
            )
        }

        private val VERSION = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")
    }
}

fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateVersion = SemanticVersion.parse(candidate) ?: return false
    val currentVersion = SemanticVersion.parse(current) ?: return false
    return candidateVersion > currentVersion
}

private fun String.isTrustedReleaseAsset(tagName: String, assetName: String): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.host?.equals(RELEASE_HOST, ignoreCase = true) == true &&
        uri.port == -1 &&
        uri.rawUserInfo == null &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        uri.rawPath == "$RELEASE_DOWNLOAD_PATH/$tagName/$assetName"
}

private const val RELEASE_HOST = "github.com"
private const val RELEASE_DOWNLOAD_PATH = "/qaz6750/CPAMP-Mobile/releases/download"
private val MARKDOWN_HEADING = Regex("^#{1,6}\\s+")
private val LANGUAGE_SECTION = Regex("(?m)^##\\s+(中文|English)\\s*$")
