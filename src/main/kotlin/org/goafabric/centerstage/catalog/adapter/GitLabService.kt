package org.goafabric.centerstage.catalog.adapter

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.persistence.entity.AdrFileEo
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class GitLabService(
    @param:RestClient val gitLabAdapter: GitLabAdapter,
    @param:ConfigProperty(name = "gitlab.token", defaultValue = "") private val token: String
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Given a GitLab tree URL like:
     *   https://gitlab.com/mygroup/myrepo/-/tree/main/catalog/adr/my-service
     * lists all .md files and returns their content as AdrFileEo list.
     */
    fun fetchAdrs(treeUrl: String): List<AdrFileEo> {
        return try {
            val (projectId, ref, path) = parseTreeUrl(treeUrl)
            val items = gitLabAdapter.listTree(projectId, path, ref, 100, token)
            items
                .filter { it.type == "blob" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .map { item ->
                    val content = gitLabAdapter.getRawFile(projectId, encodePath(item.path), ref, token)
                    AdrFileEo(name = item.name.removeSuffix(".md"), content = content)
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch ADRs from GitLab ($treeUrl): ${e.message}")
            emptyList()
        }
    }

    /**
     * Given a GitLab catalog-info URL (blob, raw, or API format) and a techdocs-ref like "dir:.",
     * fetches all .md files from the resolved docs/ directory on GitLab.
     *
     * Accepted URL formats:
     *   https://gitlab.com/mygroup/myrepo/-/raw/main/catalog/guidelines/catalog-info.yaml
     *   https://gitlab.com/mygroup/myrepo/-/blob/main/catalog/guidelines/catalog-info.yaml
     *   https://gitlab.com/api/v4/projects/mygroup%2Fmyrepo/repository/files/catalog%2Fguidelines%2Fcatalog-info.yaml/raw?ref=main
     */
    fun fetchDocs(rawCatalogUrl: String, techDocsRef: String): List<TechDoc> {
        return try {
            val (projectId, ref, catalogPath) = parseCatalogUrl(rawCatalogUrl)

            val refPath = techDocsRef.removePrefix("dir:").trim().removePrefix("./").trimEnd('/')
            val catalogDir = if (catalogPath.contains("/")) catalogPath.substringBeforeLast("/") else ""
            val docsBase = if (catalogDir.isEmpty()) "" else "$catalogDir/"
            val docsPath = if (refPath == ".") "${docsBase}docs" else "${docsBase}${refPath}/docs"

            val items = gitLabAdapter.listTree(projectId, docsPath, ref, 100, token)
            items
                .filter { it.type == "blob" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .map { item ->
                    val content = gitLabAdapter.getRawFile(projectId, encodePath(item.path), ref, token)
                    TechDoc(name = item.name.removeSuffix(".md"), content = content)
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch docs from GitLab ($rawCatalogUrl): ${e.message}")
            emptyList()
        }
    }

    /**
     * Parses a GitLab tree URL:
     *   https://gitlab.com/{namespace}/{repo}/-/tree/{ref}/{path}
     *   https://gitlab.example.com/{namespace}/{repo}/-/tree/{ref}/{path}  (self-hosted)
     *
     * Returns (urlEncodedProjectId, ref, path).
     * Project ID is encoded as "namespace%2Frepo" for the API.
     */
    private data class TreeParts(val projectId: String, val ref: String, val path: String)

    private fun parseTreeUrl(url: String): TreeParts {
        // Strip scheme + host to get the path segments
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val slashIdx = withoutScheme.indexOf('/')
        // segments after the host: [namespace..., "-", "tree"|"blob", ref, ...rest]
        val pathPart = withoutScheme.substring(slashIdx + 1).trimEnd('/')
        val parts = pathPart.split("/")
        // Accept both "/-/tree/" and "/-/blob/" — blob pointing to a directory is valid
        val markerIdx = parts.indexOfFirst { it == "tree" || it == "blob" }
        require(markerIdx >= 2 && parts.getOrNull(markerIdx - 1) == "-") { "Not a GitLab tree URL: $url" }
        val namespace = parts.take(markerIdx - 1).joinToString("/")  // supports sub-groups
        val ref  = parts[markerIdx + 1]
        val path = parts.drop(markerIdx + 2).joinToString("/")
        val projectId = URLEncoder.encode(namespace, StandardCharsets.UTF_8)
        return TreeParts(projectId, ref, path)
    }

    /**
     * Parses any GitLab catalog-info URL into (urlEncodedProjectId, ref, filePath).
     *
     * Handles:
     *   - /-/raw/{ref}/{path}   — GitLab browser raw URL
     *   - /-/blob/{ref}/{path}  — GitLab browser blob URL
     *   - /api/v4/projects/{encodedId}/repository/files/{encodedPath}/raw?ref={ref}
     */
    private fun parseCatalogUrl(url: String): TreeParts {
        // GitLab API URL: …/api/v4/projects/{id}/repository/files/{encodedPath}/raw?ref={ref}
        val apiRegex = Regex("""^https?://[^/]+/api/v4/projects/([^/]+)/repository/files/(.+)/raw\?ref=([^&]+)""")
        val apiMatch = apiRegex.find(url)
        if (apiMatch != null) {
            val projectId = apiMatch.groupValues[1]   // already URL-encoded
            val filePath  = java.net.URLDecoder.decode(apiMatch.groupValues[2], java.nio.charset.StandardCharsets.UTF_8)
            val ref       = apiMatch.groupValues[3]
            return TreeParts(projectId, ref, filePath)
        }

        // Browser blob/raw URL: /{namespace}/-/{raw|blob}/{ref}/{path}
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val slashIdx = withoutScheme.indexOf('/')
        val pathPart = withoutScheme.substring(slashIdx + 1).trimEnd('/')
        val parts = pathPart.split("/")
        val markerIdx = parts.indexOfFirst { it == "raw" || it == "blob" }
        require(markerIdx >= 2 && parts.getOrNull(markerIdx - 1) == "-") { "Not a GitLab raw/blob URL: $url" }
        val namespace = parts.take(markerIdx - 1).joinToString("/")
        val ref      = parts[markerIdx + 1]
        val filePath = parts.drop(markerIdx + 2).joinToString("/")
        val projectId = URLEncoder.encode(namespace, StandardCharsets.UTF_8)
        return TreeParts(projectId, ref, filePath)
    }

    /** URL-encodes a file path for use in the GitLab API (slashes → %2F). */
    private fun encodePath(path: String): String =
        URLEncoder.encode(path, StandardCharsets.UTF_8)
}
