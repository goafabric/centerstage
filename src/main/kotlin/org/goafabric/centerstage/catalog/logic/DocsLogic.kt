package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.DocRepository
import java.io.File

@ApplicationScoped
class DocsLogic(
    val componentRepo: ComponentRepository,
    val docRepo: DocRepository,
    val catalogLoaderLogic: CatalogLoaderLogic,
    val catalogMapper: CatalogMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {

    fun getDocs(componentName: String): List<TechDoc> =
        fromDatabase(componentName)
            ?: fromRemote(componentName)
            ?: fromLocalFiles(componentName)
            ?: emptyList()

    fun getDocsAssetFile(componentName: String, assetPath: String): File? {
        val component   = componentRepo.findByKindAndName("Component", componentName).firstOrNull() ?: return null
        val techDocsRef = component.annotation("backstage.io/techdocs-ref") ?: return null
        val sourcePath  = component.sourcePath ?: return null
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return null
        return safeAsset(resolveDocsRoot(File(sourcePath), techDocsRef), assetPath)
    }

    private fun fromDatabase(componentName: String): List<TechDoc>? =
        docRepo.findByComponentName(componentName).map { catalogMapper.toTechDoc(it) }.ifEmpty { null }

    private fun fromRemote(componentName: String): List<TechDoc>? {
        val component   = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?: throw NoSuchElementException("Component not found: $componentName")
        val techDocsRef = component.annotation("backstage.io/techdocs-ref") ?: return null
        val sourcePath  = component.sourcePath ?: return null
        return when {
            sourcePath.startsWith("https://raw.githubusercontent.com") -> gitHubService.fetchDocs(sourcePath, techDocsRef)
            remoteContentService.isGitLabUrl(sourcePath)               -> gitLabService.fetchDocs(sourcePath, techDocsRef)
            else -> null
        }
    }

    private fun fromLocalFiles(componentName: String): List<TechDoc>? {
        val component   = componentRepo.findByKindAndName("Component", componentName).firstOrNull() ?: return null
        val techDocsRef = component.annotation("backstage.io/techdocs-ref") ?: return null
        val sourcePath  = component.sourcePath ?: return null
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return null

        val docsDir = File(resolveDocsRoot(File(sourcePath), techDocsRef), "docs")
        if (!docsDir.isDirectory) return null
        return readDocsDir(docsDir, resolveDocsRoot(File(sourcePath), techDocsRef))
    }

    private fun readDocsDir(docsDir: File, docsRoot: File): List<TechDoc> {
        val navOrder = parseMkDocsNav(File(docsRoot, "mkdocs.yml"))
        return if (navOrder.isNotEmpty())
            navOrder.mapNotNull { (title, filename) ->
                File(docsDir, filename).takeIf { it.exists() }?.let { TechDoc(title, it.readText()) }
            }
        else
            docsDir.listFiles { f -> f.extension == "md" }
                ?.sortedBy { it.name }
                ?.map { TechDoc(it.nameWithoutExtension, it.readText()) }
                ?: emptyList()
    }

    private fun resolveDocsRoot(sourceFile: File, techDocsRef: String): File {
        val refPath = techDocsRef.removePrefix("dir:").trim()
        return if (refPath == ".") sourceFile.parentFile else File(sourceFile.parentFile, refPath)
    }

    private fun safeAsset(docsRoot: File, assetPath: String): File? {
        val asset = File(docsRoot, "docs/$assetPath").canonicalFile
        return asset.takeIf { it.startsWith(docsRoot.canonicalFile) && it.exists() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMkDocsNav(mkdocsFile: File): List<Pair<String, String>> {
        if (!mkdocsFile.exists()) return emptyList()
        return runCatching {
            val nav = (catalogLoaderLogic.yamlMapper.readValue(mkdocsFile, Map::class.java)["nav"] as? List<*>)
                ?: return emptyList()
            nav.mapNotNull { entry ->
                val kv = (entry as? Map<*, *>)?.entries?.firstOrNull() ?: return@mapNotNull null
                kv.key.toString() to kv.value.toString()
            }
        }.getOrDefault(emptyList())
    }
}
