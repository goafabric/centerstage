package org.goafabric.centerstage.adapter

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.goafabric.centerstage.controller.dto.TechDoc
import org.goafabric.centerstage.persistence.entity.AdrEo
import org.slf4j.LoggerFactory

@ApplicationScoped
class GitHubService(
    @RestClient val gitHubAdapter: GitHubAdapter,
    val remoteContentService: RemoteContentService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun fetchDocs(rawCatalogUrl: String, techDocsRef: String): List<TechDoc> {
        return try {
            val withoutScheme = rawCatalogUrl.removePrefix("https://raw.githubusercontent.com/").trimEnd('/')
            val parts = withoutScheme.split("/")
            val owner = parts[0]; val repo = parts[1]; val ref = parts[2]
            val catalogPath = parts.drop(3).joinToString("/")
            val refPath = techDocsRef.removePrefix("dir:").trim().removePrefix("./").trimEnd('/')
            val catalogDir = if (catalogPath.contains("/")) catalogPath.substringBeforeLast("/") else ""
            val docsBase = if (catalogDir.isEmpty()) "" else "$catalogDir/"
            val docsPath = if (refPath == ".") "${docsBase}docs" else "${docsBase}${refPath}/docs"

            gitHubAdapter.listContents(owner, repo, docsPath, ref, "centerstage")
                .filter { it.type == "file" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .mapNotNull { file ->
                    val downloadUrl = file.downloadUrl ?: return@mapNotNull null
                    TechDoc(name = file.name.removeSuffix(".md"), content = remoteContentService.fetchText(downloadUrl))
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch docs from GitHub ($rawCatalogUrl): ${e.message}")
            emptyList()
        }
    }

    fun fetchAdrs(treeUrl: String): List<AdrEo> {
        return try {
            val (owner, repo, ref, path) = parseTreeUrl(treeUrl)
            gitHubAdapter.listContents(owner, repo, path, ref, "centerstage")
                .filter { it.type == "file" && it.name.endsWith(".md") }
                .sortedBy { it.name }
                .mapNotNull { file ->
                    val downloadUrl = file.downloadUrl ?: return@mapNotNull null
                    AdrEo().apply {
                        name    = file.name.removeSuffix(".md")
                        content = remoteContentService.fetchText(downloadUrl)
                    }
                }
        } catch (e: Exception) {
            log.warn("Failed to fetch ADRs from GitHub ($treeUrl): ${e.message}")
            emptyList()
        }
    }

    private data class TreeParts(val owner: String, val repo: String, val ref: String, val path: String)

    private fun parseTreeUrl(url: String): TreeParts {
        val parts = url.removePrefix("https://github.com/").trimEnd('/').split("/")
        require(parts.size >= 4 && parts[2] == "tree") { "Not a GitHub tree URL: $url" }
        return TreeParts(parts[0], parts[1], parts[3], parts.drop(4).joinToString("/"))
    }
}
