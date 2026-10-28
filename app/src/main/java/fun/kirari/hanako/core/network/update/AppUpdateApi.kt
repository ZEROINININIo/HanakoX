package `fun`.kirari.hanako.core.network.update

import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.network.NetworkClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class AppUpdateInfo(
    val version: String,
    val releaseName: String,
    val releaseUrl: String,
    val changelogMarkdown: String
)

internal class AppUpdateApi(
    private val clientProvider: NetworkClientProvider,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val tag = "HanakoAppUpdate"

    suspend fun checkForUpdate(): AppUpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching { checkLatestRelease() }
                .onFailure { error ->
                    AppDebugLogStore.e(tag, "latest release check failed: ${error.message}")
                }
                .getOrNull()
                ?: runCatching { checkReadmeReleaseLink() }
                    .onFailure { error ->
                        AppDebugLogStore.e(tag, "readme update fallback failed: ${error.message}")
                    }
                    .getOrNull()
        }

    private fun checkLatestRelease(): AppUpdateInfo? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HanakoAI/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        return clientProvider.clientWithTimeout(trustAllHttpsCertificates = false, timeoutMillis = TIMEOUT_MILLIS)
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    AppDebugLogStore.e(tag, "latest release failed: HTTP ${response.code}")
                    return null
                }
                parseLatestRelease(response.body?.string().orEmpty())
            }
    }

    private fun checkReadmeReleaseLink(): AppUpdateInfo? {
        val request = Request.Builder()
            .url(README_RAW_URL)
            .header("User-Agent", "HanakoAI/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

        return clientProvider.clientWithTimeout(trustAllHttpsCertificates = false, timeoutMillis = TIMEOUT_MILLIS)
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    AppDebugLogStore.e(tag, "readme update fallback failed: HTTP ${response.code}")
                    return null
                }
                extractReadmeUpdateInfo(
                    markdown = response.body?.string().orEmpty(),
                    currentVersion = BuildConfig.VERSION_NAME
                )
            }
    }

    private fun parseLatestRelease(body: String): AppUpdateInfo? {
        if (body.isBlank()) return null
        val root = json.parseToJsonElement(body).jsonObject
        val tagName = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
        if (!isRemoteVersionNewer(tagName, BuildConfig.VERSION_NAME)) return null

        val releaseUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty()
        if (releaseUrl.isBlank()) return null

        val releaseName = root["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: tagName
        val bodyMarkdown = root["body"]?.jsonPrimitive?.content.orEmpty()
        return AppUpdateInfo(
            version = tagName.removePrefix("v"),
            releaseName = releaseName,
            releaseUrl = releaseUrl,
            changelogMarkdown = extractReleaseChangelogMarkdown(bodyMarkdown)
        )
    }

    private companion object {
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/zyf2007/HanakoAI/releases/latest"
        private const val README_RAW_URL = "https://raw.githubusercontent.com/zyf2007/HanakoAI/main/README.md"
        private const val TIMEOUT_MILLIS = 12_000L
    }
}

internal fun extractReadmeUpdateInfo(markdown: String, currentVersion: String): AppUpdateInfo? {
    val download = Regex("""\[\[Download\s+([^\]]+)]\(([^)]+)\)]""", RegexOption.IGNORE_CASE)
        .find(markdown)
        ?: return null
    val version = download.groupValues[1].trim()
    if (!isRemoteVersionNewer(version, currentVersion)) return null

    val releaseUrl = Regex("""\[\[View Release Notes]\(([^)]+)\)]""", RegexOption.IGNORE_CASE)
        .find(markdown)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: download.groupValues[2].trim()

    return AppUpdateInfo(
        version = version.removePrefix("v"),
        releaseName = "v${version.removePrefix("v")}",
        releaseUrl = releaseUrl,
        changelogMarkdown = extractReadmeChangelogMarkdown(markdown)
    )
}

internal fun extractReadmeChangelogMarkdown(markdown: String): String {
    val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
    val changelogHeading = Regex("""(?im)^##\s+Changelog\s*$""")
        .find(normalized)
        ?: return ""
    return normalized.substring(changelogHeading.range.last + 1).trim()
}

internal fun isRemoteVersionNewer(remote: String, current: String): Boolean {
    val remoteVersion = ComparableVersion.parse(remote)
    val currentVersion = ComparableVersion.parse(current)
    return remoteVersion != null && currentVersion != null && remoteVersion > currentVersion
}

private data class ComparableVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prereleaseRank: Int
) : Comparable<ComparableVersion> {
    override fun compareTo(other: ComparableVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        return compareValues(prereleaseRank, other.prereleaseRank)
    }

    companion object {
        fun parse(value: String): ComparableVersion? {
            val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+.]([A-Za-z0-9.-]+))?.*$""")
                .find(value.trim())
                ?: return null
            val prerelease = match.groupValues.getOrNull(4).orEmpty().lowercase()
            return ComparableVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                prereleaseRank = when {
                    prerelease.isBlank() -> 3
                    prerelease.startsWith("rc") -> 2
                    prerelease.startsWith("beta") -> 1
                    else -> 0
                }
            )
        }
    }
}

internal fun extractReleaseChangelogMarkdown(markdown: String): String {
    val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isBlank()) return "这个版本没有填写更新日志。"

    val changelogHeading = Regex("""(?im)^##\s+(?:changelog|更新日志)\s*$""")
        .find(normalized)
    val start = changelogHeading?.range?.last?.plus(1) ?: 0
    val afterStart = normalized.substring(start).trim()
    val end = Regex("""(?im)^##\s+(?:下载提示|下载说明|downloads?)\s*$""")
        .find(afterStart)
        ?.range
        ?.first
        ?: Regex("""(?m)^##\s+.+$""")
            .find(afterStart)
            ?.range
            ?.first
        ?: afterStart.length

    return afterStart.substring(0, end).trim().ifBlank { "这个版本没有填写更新日志。" }
}
