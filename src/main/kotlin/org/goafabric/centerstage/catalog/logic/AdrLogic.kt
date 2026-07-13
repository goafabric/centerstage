package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.AdrRepository
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import java.io.File

@ApplicationScoped
class AdrLogic(
    val componentRepo: ComponentRepository,
    val adrRepo: AdrRepository,
    val catalogMapper: CatalogMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {

    fun getAdrs(componentName: String): List<Adr> =
        fromDatabase(componentName)
            ?: fromRemote(componentName)
            ?: fromLocalFiles(componentName)
            ?: emptyList()

    private fun fromDatabase(componentName: String): List<Adr>? =
        adrRepo.findByComponentName(componentName).map { catalogMapper.toAdr(it) }.ifEmpty { null }

    private fun fromRemote(componentName: String): List<Adr>? {
        val component   = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?: throw NoSuchElementException("Component not found: $componentName")
        val adrLocation = component.annotation("backstage.io/adr-location") ?: return null

        return when {
            adrLocation.startsWith("https://github.com")  -> gitHubService.fetchAdrs(adrLocation)
            remoteContentService.isGitLabUrl(adrLocation) -> gitLabService.fetchAdrs(adrLocation)
            else -> null
        }?.map { catalogMapper.toAdr(it) }
    }

    private fun fromLocalFiles(componentName: String): List<Adr>? {
        val component   = componentRepo.findByKindAndName("Component", componentName).firstOrNull() ?: return null
        val adrLocation = component.annotation("backstage.io/adr-location")
        val sourcePath  = component.sourcePath ?: return null
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return null

        val catalogDir = resolveParentDir(File(sourcePath))
        val candidates = listOfNotNull(adrLocation?.trimEnd('/')?.substringAfterLast('/'), componentName).distinct()
        return candidates.firstNotNullOfOrNull { candidate ->
            File(catalogDir, "adr/$candidate").takeIf { it.isDirectory }?.let { readAdrFiles(it) }
        }
    }

    private fun readAdrFiles(dir: File): List<Adr> =
        dir.listFiles { f -> f.extension == "md" }
            ?.sortedBy { it.name }
            ?.map { catalogMapper.toAdr(AdrEo().apply { name = it.nameWithoutExtension; content = it.readText() }) }
            ?: emptyList()

    private fun resolveParentDir(file: File): File {
        val resolved = if (file.isAbsolute) file else File(System.getProperty("user.dir"), file.path)
        return resolved.parentFile ?: resolved
    }
}
