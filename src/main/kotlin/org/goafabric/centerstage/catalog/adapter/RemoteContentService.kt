package org.goafabric.centerstage.catalog.adapter

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


/**
 * Single entry point for all outbound HTTP content fetching.
 * Knows which auth headers to attach per provider based on configured base URLs.
 * No other class should open HTTP connections directly.
 */
@ApplicationScoped
class RemoteContentService(
    @param:ConfigProperty(name = "gitlab.token", defaultValue = "") private val gitlabToken: String
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Converts a browser-facing blob/tree URL to a directly fetchable raw URL.
     *
     *   GitHub:
     *     https://github.com/{owner}/{repo}/blob/{ref}/{path}
     *     → https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}
     *
     *   GitLab blob (browser URL):
     *     https://{host}/{ns[/sub]}/{repo}/-/blob/{ref}/{path/to/file}
     *     → https://{host}/api/v4/projects/{ns%2F[sub%2F]repo}/repository/files/{path%2Fto%2Ffile}/raw?ref={ref}
     *
     *   GitLab raw (already converted or direct raw URL) — returned as-is.
     */
    fun toRawUrl(url: String): String = when {
        url.contains("github.com") && url.contains("/blob/") ->
            url.replace("https://github.com", "https://raw.githubusercontent.com").replace("/blob/", "/")
        url.contains("/-/blob/") ->
            gitLabBlobToApiUrl(url)
        else -> url
    }

    /**
     * Converts a GitLab blob browser URL to the GitLab API v4 raw file endpoint.
     *
     * Input:  https://git.example.com/ns/sub/repo/-/blob/develop/catalog/file.yaml
     * Output: https://git.example.com/api/v4/projects/ns%2Fsub%2Frepo/repository/files/catalog%2Ffile.yaml/raw?ref=develop
     */
    private fun gitLabBlobToApiUrl(blobUrl: String): String {
        // Strip scheme to parse the rest
        val schemeEnd = blobUrl.indexOf("://")
        val scheme = blobUrl.substring(0, schemeEnd)          // "https"
        val afterScheme = blobUrl.substring(schemeEnd + 3)    // "git.example.com/ns/sub/repo/-/blob/develop/path/file.yaml"

        val hostEnd = afterScheme.indexOf('/')
        val host = afterScheme.substring(0, hostEnd)          // "git.example.com"
        val pathPart = afterScheme.substring(hostEnd + 1)     // "ns/sub/repo/-/blob/develop/path/file.yaml"

        val parts = pathPart.split("/")
        val blobIdx = parts.indexOf("blob")
        // parts[blobIdx - 1] must be "-"
        require(blobIdx >= 2 && parts.getOrNull(blobIdx - 1) == "-") { "Not a GitLab blob URL: $blobUrl" }

        // Everything before "/-/" is the namespace + repo
        val namespace = parts.take(blobIdx - 1).joinToString("/")   // e.g. "ns/sub/repo"
        val ref       = parts[blobIdx + 1]                           // e.g. "develop"
        val filePath  = parts.drop(blobIdx + 2).joinToString("/")    // e.g. "catalog/file.yaml"

        val encodedProject  = URLEncoder.encode(namespace, StandardCharsets.UTF_8)  // "ns%2Fsub%2Frepo"
        val encodedFilePath = URLEncoder.encode(filePath,  StandardCharsets.UTF_8)  // "catalog%2Ffile.yaml"

        return "$scheme://$host/api/v4/projects/$encodedProject/repository/files/$encodedFilePath/raw?ref=$ref"
    }

    /**
     * Returns true for GitLab URLs — detected by the GitLab-specific path pattern `/-/`
     * or by the presence of `/api/v4/` (already-converted API URLs).
     */
    fun isGitLabUrl(url: String): Boolean = url.contains("/-/") || url.contains("/api/v4/")

    /**
     * Fetches the content of [rawUrl] as a string, attaching the appropriate
     * auth header based on which SCM host the URL belongs to.
     */
    fun fetchText(rawUrl: String): String {
        log.debug("Fetching: $rawUrl")
        val connection = URI(rawUrl).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "centerstage")

        if (gitlabToken.isNotBlank() && isGitLabUrl(rawUrl)) {
            connection.setRequestProperty("PRIVATE-TOKEN", gitlabToken)
        }

        return connection.inputStream.bufferedReader().readText()
    }

    /** Convenience wrapper that returns null on failure instead of throwing. */
    fun fetchTextOrNull(rawUrl: String): String? =
        try { fetchText(rawUrl) }
        catch (e: Exception) {
            log.warn("Failed to fetch $rawUrl: ${e.message}")
            null
        }
}
