package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.AdrFileEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper
import java.io.File

@ApplicationScoped
class AdrLogic(
    val persistenceMapper: PersistenceMapper,
    val catalogMapper: CatalogMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {
    @Inject lateinit var componentRepo: ComponentEo.Repo
    @Inject lateinit var adrRepo: AdrEo.Repo

    fun getAdrs(componentName: String): List<Adr> {
        val persisted = adrRepo.findByComponentName(componentName)
        if (persisted.isNotEmpty()) return persisted.map { catalogMapper.toAdr(it) }

        val component = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?.let { persistenceMapper.toCatalogEo(it) }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val adrLocation = component.metadata.annotations["backstage.io/adr-location"]

        if (adrLocation != null && adrLocation.startsWith("https://github.com"))
            return gitHubService.fetchAdrs(adrLocation).map { catalogMapper.toAdr(it) }
        if (adrLocation != null && remoteContentService.isGitLabUrl(adrLocation))
            return gitLabService.fetchAdrs(adrLocation).map { catalogMapper.toAdr(it) }

        val sourcePath = component.sourcePath ?: return emptyList()
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return emptyList()
        val catalogDir = resolveDir(File(sourcePath))
        val candidates = buildList {
            if (adrLocation != null) add(adrLocation.trimEnd('/').substringAfterLast('/'))
            add(componentName)
        }.distinct()
        for (candidate in candidates) {
            val adrDir = File(catalogDir, "adr/$candidate")
            if (adrDir.exists() && adrDir.isDirectory) return readAdrFiles(adrDir)
        }
        return emptyList()
    }

    private fun readAdrFiles(dir: File): List<Adr> =
        dir.listFiles { f -> f.extension == "md" }
            ?.sortedBy { it.name }
            ?.map { catalogMapper.toAdr(AdrFileEo(name = it.nameWithoutExtension, content = it.readText())) }
            ?: emptyList()

    private fun resolveDir(catalogFile: File): File {
        val f = if (catalogFile.isAbsolute) catalogFile
                else File(System.getProperty("user.dir"), catalogFile.path)
        return f.parentFile ?: f
    }
}
