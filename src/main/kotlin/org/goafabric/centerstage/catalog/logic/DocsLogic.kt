package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper
import java.io.File

@ApplicationScoped
class DocsLogic(
    val persistenceMapper: PersistenceMapper,
    val catalogLoaderLogic: CatalogLoaderLogic,
    val catalogMapper: CatalogMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {
    @Inject lateinit var componentRepo: ComponentEo.Repo
    @Inject lateinit var docRepo: DocEo.Repo

    fun getDocs(componentName: String): List<TechDoc> {
        val persisted = docRepo.findByComponentName(componentName)
        if (persisted.isNotEmpty()) return persisted.map { catalogMapper.toTechDoc(it) }

        val component = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?.let { persistenceMapper.toCatalogEo(it) }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val techDocsRef = component.metadata.annotations["backstage.io/techdocs-ref"] ?: return emptyList()
        val sourcePath = component.sourcePath ?: return emptyList()

        if (sourcePath.startsWith("https://raw.githubusercontent.com"))
            return gitHubService.fetchDocs(sourcePath, techDocsRef)
        if (remoteContentService.isGitLabUrl(sourcePath))
            return gitLabService.fetchDocs(sourcePath, techDocsRef)

        val sourceFile = File(sourcePath)
        val refPath = techDocsRef.removePrefix("dir:").trim()
        val docsRoot = if (refPath == ".") sourceFile.parentFile else File(sourceFile.parentFile, refPath)
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
        val component = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?.let { persistenceMapper.toCatalogEo(it) }
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
