package org.goafabric.centerstage.catalog.adapter

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.persistence.entity.AdrFileEo
import org.slf4j.LoggerFactory
import java.net.URI

@ApplicationScoped
class GitHubService(
    @RestClient val gitHubAdapter: GitHubAdapter,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val authHeader = ""

    /**
     * Given a raw.githubusercontent.com catalog-info URL and a techdocs-ref like "dir:.",
     * fetches all .md files from the resolved docs/ directory on GitHub.
     * rawCatalogUrl example: https://raw.githubusercontent.com/goafabric/backstage/develop/catalog/guidelines/catalog-info.yaml
     */
    fun fetchDocs(rawCatalogUrl: String, techDocsRef: String): List<TechDoc> {
        return try {
            // Convert raw URL back to API-friendly parts
            // https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path/to/catalog-info.yaml}
            val withoutScheme = rawCatalogUrl.removePrefix("https://raw.githubusercontent.com/").trimEnd('/')
            val parts = withoutScheme.split("/")
            val owner = parts[0]
            val repo  = parts[1]
            val ref   = parts[2]
            val catalogPath = parts.drop(3).joinToString("/")  // e.g. catalog/guidelines/catalog-info.yaml

            val refPath = techDocsRef.removePrefix("dir:").trim().removePrefix("./").trimEnd('/')
            // catalogDir is the directory containing catalog-info.yaml; empty string if it's at repo root
            val catalogDir = if (catalogPath.contains("/")) catalogPath.substringBeforeLast("/") else ""
            val docsBase = if (catalogDir.isEmpty()) "" else "$catalogDir/"
            val docsPath = if (refPath == ".") "${docsBase}docs" else "${docsBase}${refPath}/docs"

            val files = gitHubAdapter.listContents(owner, repo, docsPath, ref, authHeader, "centerstage")
            files
                .filter { it.type == "file" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .mapNotNull { file ->
                    val downloadUrl = file.downloadUrl ?: return@mapNotNull null
                    val content = fetchRawContent(downloadUrl)
                    TechDoc(name = file.name.removeSuffix(".md"), content = content)
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch docs from GitHub ($rawCatalogUrl): ${e.message}")
            emptyList()
        }
    }

    /**
     * Given a GitHub tree URL like:
     *   https://github.com/goafabric/backstage/tree/develop/catalog/adr/catalog-service
     * lists all .md files and returns their content as AdrFileEo list.
     */
    fun fetchAdrs(treeUrl: String): List<AdrFileEo> {
        return try {
            val (owner, repo, ref, path) = parseTreeUrl(treeUrl)
            val files = gitHubAdapter.listContents(owner, repo, path, ref, authHeader, "centerstage")
            files
                .filter { it.type == "file" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .mapNotNull { file ->
                    val downloadUrl = file.downloadUrl ?: return@mapNotNull null
                    val content = fetchRawContent(downloadUrl)
                    AdrFileEo(name = file.name.removeSuffix(".md"), content = content)
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch ADRs from GitHub (${treeUrl}): ${e.message}")
            emptyList()
        }
    }

    private fun fetchRawContent(rawUrl: String): String {
        val connection = URI(rawUrl).toURL().openConnection()
        connection.setRequestProperty("User-Agent", "centerstage")
        return connection.getInputStream().bufferedReader().readText()
    }

    /**
     * Parses https://github.com/{owner}/{repo}/tree/{ref}/{path}
     * Returns (owner, repo, ref, path)
     */
    private data class TreeParts(val owner: String, val repo: String, val ref: String, val path: String)

    private fun parseTreeUrl(url: String): TreeParts {
        // https://github.com/goafabric/backstage/tree/develop/catalog/adr/core-service
        val withoutScheme = url.removePrefix("https://github.com/").trimEnd('/')
        val parts = withoutScheme.split("/")
        // parts: [owner, repo, "tree", ref, ...rest]
        require(parts.size >= 4 && parts[2] == "tree") { "Not a GitHub tree URL: $url" }
        val owner = parts[0]
        val repo  = parts[1]
        val ref   = parts[3]
        val path  = parts.drop(4).joinToString("/")
        return TreeParts(owner, repo, ref, path)
    }
}
