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
    @RestClient val gitLabAdapter: GitLabAdapter,
    @ConfigProperty(name = "gitlab.token", defaultValue = "") val configToken: String
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val token: String
        get() = configToken.ifBlank { System.getenv("GITLAB_TOKEN") ?: "" }

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
     * Given a raw GitLab catalog-info URL and a techdocs-ref like "dir:.",
     * fetches all .md files from the resolved docs/ directory on GitLab.
     *
     * rawCatalogUrl example:
     *   https://gitlab.com/mygroup/myrepo/-/raw/main/catalog/guidelines/catalog-info.yaml
     */
    fun fetchDocs(rawCatalogUrl: String, techDocsRef: String): List<TechDoc> {
        return try {
            val (projectId, ref, catalogPath) = parseRawUrl(rawCatalogUrl)

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
        // segments after the host: [namespace, repo, "-", "tree", ref, ...rest]
        val pathPart = withoutScheme.substring(slashIdx + 1).trimEnd('/')
        val parts = pathPart.split("/")
        // Find "/-/tree/" marker
        val treeIdx = parts.indexOf("tree")
        require(treeIdx >= 2 && parts.getOrNull(treeIdx - 1) == "-") { "Not a GitLab tree URL: $url" }
        val namespace = parts.take(treeIdx - 1).joinToString("/")  // supports sub-groups
        val ref  = parts[treeIdx + 1]
        val path = parts.drop(treeIdx + 2).joinToString("/")
        val projectId = URLEncoder.encode(namespace, StandardCharsets.UTF_8)
        return TreeParts(projectId, ref, path)
    }

    /**
     * Parses a GitLab raw file URL:
     *   https://gitlab.com/{namespace}/{repo}/-/raw/{ref}/{path/to/file}
     *
     * Returns (urlEncodedProjectId, ref, filePath).
     */
    private fun parseRawUrl(url: String): TreeParts {
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val slashIdx = withoutScheme.indexOf('/')
        val pathPart = withoutScheme.substring(slashIdx + 1).trimEnd('/')
        val parts = pathPart.split("/")
        val rawIdx = parts.indexOf("raw")
        require(rawIdx >= 2 && parts.getOrNull(rawIdx - 1) == "-") { "Not a GitLab raw URL: $url" }
        val namespace = parts.take(rawIdx - 1).joinToString("/")
        val ref      = parts[rawIdx + 1]
        val filePath = parts.drop(rawIdx + 2).joinToString("/")
        val projectId = URLEncoder.encode(namespace, StandardCharsets.UTF_8)
        return TreeParts(projectId, ref, filePath)
    }

    /** URL-encodes a file path for use in the GitLab API (slashes → %2F). */
    private fun encodePath(path: String): String =
        URLEncoder.encode(path, StandardCharsets.UTF_8)
}
