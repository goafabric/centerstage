package org.goafabric.centerstage.catalog.adapter

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI

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
     *   GitHub:  https://github.com/{owner}/{repo}/blob/{ref}/{path}  →  https://raw.githubusercontent.com/...
     *   GitLab:  https://{host}/{ns}/{repo}/-/blob/{ref}/{path}       →  same host /-/raw/{ref}/{path}
     */
    fun toRawUrl(url: String): String = when {
        url.contains("github.com") && url.contains("/blob/") ->
            url.replace("https://github.com", "https://raw.githubusercontent.com").replace("/blob/", "/")
        url.contains("/-/blob/") ->
            url.replace("/-/blob/", "/-/raw/")
        else -> url
    }

    /**
     * Returns true for GitLab URLs — detected by the GitLab-specific path pattern `/-/`
     * which is present in all GitLab blob/raw/tree URLs regardless of hostname.
     */
    fun isGitLabUrl(url: String): Boolean = url.contains("/-/")

    /**
     * Fetches the content of [rawUrl] as a string, attaching the appropriate
     * auth header based on which SCM host the URL belongs to.
     */
    fun fetchText(rawUrl: String): String {
        log.debug("Fetching: $rawUrl")
        val connection = URI(rawUrl).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "centerstage")

        val glToken = gitlabToken  // config value, or GITLAB_TOKEN env var
        if (glToken.isNotBlank() && isGitLabUrl(rawUrl)) {
            connection.setRequestProperty("PRIVATE-TOKEN", glToken)
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
