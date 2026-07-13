package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import java.io.File

@ApplicationScoped
class DocsLogic(
    val catalogLoaderLogic: CatalogLoaderLogic,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {

    fun getDocs(componentName: String): List<TechDoc> {
        val component = catalogLoaderLogic.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val techDocsRef = component.metadata.annotations["backstage.io/techdocs-ref"]
            ?: return emptyList()

        val sourcePath = component.sourcePath ?: return emptyList()

        // Remote: sourcePath is a raw.githubusercontent.com URL — fetch docs via GitHub API
        if (sourcePath.startsWith("https://raw.githubusercontent.com")) {
            return gitHubService.fetchDocs(sourcePath, techDocsRef)
        }

        // Remote: sourcePath is a GitLab URL (blob, raw, or API) — fetch docs via GitLab API
        if (remoteContentService.isGitLabUrl(sourcePath)) {
            return gitLabService.fetchDocs(sourcePath, techDocsRef)
        }

        // Local: resolve the docs/ directory relative to the catalog-info.yaml file
        val sourceFile = File(sourcePath)
        val refPath = techDocsRef.removePrefix("dir:").trim()
        val docsRoot = if (refPath == ".") sourceFile.parentFile
                       else File(sourceFile.parentFile, refPath)
        val docsDir = File(docsRoot, "docs")
        if (!docsDir.exists() || !docsDir.isDirectory) return emptyList()

        val navOrder = parseMkDocsNav(File(docsRoot, "mkdocs.yml"))

        return if (navOrder.isNotEmpty()) {
            navOrder.mapNotNull { (title, filename) ->
                val file = File(docsDir, filename)
                if (file.exists()) TechDoc(name = title, content = file.readText()) else null
            }
        } else {
            docsDir.listFiles { f -> f.extension == "md" }
                ?.sortedBy { it.name }
                ?.map { TechDoc(name = it.nameWithoutExtension, content = it.readText()) }
                ?: emptyList()
        }
    }

    fun getDocsAssetFile(componentName: String, assetPath: String): File? {
        val component = catalogLoaderLogic.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: return null

        val techDocsRef = component.metadata.annotations["backstage.io/techdocs-ref"] ?: return null
        val sourcePath = component.sourcePath ?: return null
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return null

        val sourceFile = File(sourcePath)
        val refPath = techDocsRef.removePrefix("dir:").trim()
        val docsRoot = if (refPath == ".") sourceFile.parentFile else File(sourceFile.parentFile, refPath)
        val asset = File(docsRoot, "docs/$assetPath").canonicalFile
        return if (asset.startsWith(docsRoot.canonicalFile) && asset.exists()) asset else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMkDocsNav(mkdocsFile: File): List<Pair<String, String>> {
        if (!mkdocsFile.exists()) return emptyList()
        return try {
            val yaml = catalogLoaderLogic.yamlMapper.readValue(mkdocsFile, Map::class.java)
            val nav = yaml["nav"] as? List<*> ?: return emptyList()
            nav.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val kv = map.entries.firstOrNull() ?: return@mapNotNull null
                kv.key.toString() to kv.value.toString()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
