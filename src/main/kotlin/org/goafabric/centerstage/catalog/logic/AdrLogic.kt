package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.entity.AdrFileEo
import java.io.File

@ApplicationScoped
class AdrLogic(
    val catalogLoaderLogic: CatalogLoaderLogic,
    val catalogMapper: CatalogMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {

    fun getAdrs(componentName: String): List<Adr> {
        val component = catalogLoaderLogic.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val adrLocation = component.metadata.annotations["backstage.io/adr-location"]

        // Remote GitHub URL — fetch via API
        if (adrLocation != null && adrLocation.startsWith("https://github.com")) {
            return gitHubService.fetchAdrs(adrLocation).map { catalogMapper.toAdr(it) }
        }

        // Remote GitLab URL — fetch via API
        if (adrLocation != null && remoteContentService.isGitLabUrl(adrLocation)) {
            return gitLabService.fetchAdrs(adrLocation).map { catalogMapper.toAdr(it) }
        }

        // Local fallback: only works when catalog is loaded from local filesystem
        val catalogFile = catalogLoaderLogic.catalogFile
        if (catalogFile.startsWith("http://") || catalogFile.startsWith("https://")) return emptyList()
        val catalogDir = resolveDir(File(catalogFile))
        val candidates = buildList {
            if (adrLocation != null) {
                add(adrLocation.trimEnd('/').substringAfterLast('/'))
            }
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
