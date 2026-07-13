package org.goafabric.centerstage.catalog.adapter

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class GitLabService(
    @param:RestClient val gitLabAdapter: GitLabAdapter,
    @param:ConfigProperty(name = "gitlab.token", defaultValue = "") private val token: String
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun fetchAdrs(treeUrl: String): List<AdrEo> {
        return try {
            val (projectId, ref, path) = parseTreeUrl(treeUrl)
            gitLabAdapter.listTree(projectId, path, ref, 100, token)
                .filter { it.type == "blob" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .map { item ->
                    AdrEo().apply {
                        name    = item.name.removeSuffix(".md")
                        content = gitLabAdapter.getRawFile(projectId, encodePath(item.path), ref, token)
                    }
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch ADRs from GitLab ($treeUrl): ${e.message}")
            emptyList()
        }
    }

    fun fetchDocs(rawCatalogUrl: String, techDocsRef: String): List<TechDoc> {
        return try {
            val (projectId, ref, catalogPath) = parseCatalogUrl(rawCatalogUrl)
            val refPath = techDocsRef.removePrefix("dir:").trim().removePrefix("./").trimEnd('/')
            val catalogDir = if (catalogPath.contains("/")) catalogPath.substringBeforeLast("/") else ""
            val docsBase = if (catalogDir.isEmpty()) "" else "$catalogDir/"
            val docsPath = if (refPath == ".") "${docsBase}docs" else "${docsBase}${refPath}/docs"

            gitLabAdapter.listTree(projectId, docsPath, ref, 100, token)
                .filter { it.type == "blob" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .map { item ->
                    TechDoc(
                        name    = item.name.removeSuffix(".md"),
                        content = gitLabAdapter.getRawFile(projectId, encodePath(item.path), ref, token)
                    )
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch docs from GitLab ($rawCatalogUrl): ${e.message}")
            emptyList()
        }
    }

    private data class TreeParts(val projectId: String, val ref: String, val path: String)

    private fun parseTreeUrl(url: String): TreeParts {
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val pathPart = withoutScheme.substring(withoutScheme.indexOf('/') + 1).trimEnd('/')
        val parts = pathPart.split("/")
        val markerIdx = parts.indexOfFirst { it == "tree" || it == "blob" }
        require(markerIdx >= 2 && parts.getOrNull(markerIdx - 1) == "-") { "Not a GitLab tree URL: $url" }
        return TreeParts(
            projectId = URLEncoder.encode(parts.take(markerIdx - 1).joinToString("/"), StandardCharsets.UTF_8),
            ref  = parts[markerIdx + 1],
            path = parts.drop(markerIdx + 2).joinToString("/")
        )
    }

    private fun parseCatalogUrl(url: String): TreeParts {
        val apiRegex = Regex("""^https?://[^/]+/api/v4/projects/([^/]+)/repository/files/(.+)/raw\?ref=([^&]+)""")
        val m = apiRegex.find(url)
        if (m != null) return TreeParts(
            projectId = m.groupValues[1],
            ref       = m.groupValues[3],
            path      = java.net.URLDecoder.decode(m.groupValues[2], StandardCharsets.UTF_8)
        )
        val withoutScheme = url.removePrefix("https://").removePrefix("http://")
        val pathPart = withoutScheme.substring(withoutScheme.indexOf('/') + 1).trimEnd('/')
        val parts = pathPart.split("/")
        val markerIdx = parts.indexOfFirst { it == "raw" || it == "blob" }
        require(markerIdx >= 2 && parts.getOrNull(markerIdx - 1) == "-") { "Not a GitLab raw/blob URL: $url" }
        return TreeParts(
            projectId = URLEncoder.encode(parts.take(markerIdx - 1).joinToString("/"), StandardCharsets.UTF_8),
            ref       = parts[markerIdx + 1],
            path      = parts.drop(markerIdx + 2).joinToString("/")
        )
    }

    private fun encodePath(path: String): String = URLEncoder.encode(path, StandardCharsets.UTF_8)
}
