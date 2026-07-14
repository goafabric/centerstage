package org.goafabric.centerstage.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.controller.dto.Adr
import org.goafabric.centerstage.logic.mapper.AdrMapper
import org.goafabric.centerstage.persistence.AdrRepository
import org.goafabric.centerstage.persistence.ComponentRepository
import org.goafabric.centerstage.persistence.entity.AdrEo
import java.io.File

@ApplicationScoped
class AdrLogic(
    val componentRepo: ComponentRepository,
    val adrRepo: AdrRepository,
    val adrMapper: AdrMapper
) {

    fun getComponentNamesWithAdrs(): List<String> =
        adrRepo.findComponentNamesWithAdrs()

    fun getAdrs(componentName: String): List<Adr> =
        fromDatabase(componentName)
            ?: fromLocalFiles(componentName)
            ?: emptyList()

    private fun fromDatabase(componentName: String): List<Adr>? =
        adrRepo.findByComponentName(componentName).map { adrMapper.toAdr(it) }.ifEmpty { null }

    private fun fromLocalFiles(componentName: String): List<Adr>? {
        val component  = componentRepo.findByKindAndName("Component", componentName).firstOrNull() ?: return null
        val sourcePath = component.sourcePath ?: return null
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return null

        val adrLocation = component.annotation("backstage.io/adr-location")
        val catalogDir  = resolveParentDir(File(sourcePath))
        val candidates  = listOfNotNull(adrLocation?.trimEnd('/')?.substringAfterLast('/'), componentName).distinct()
        return candidates.firstNotNullOfOrNull { candidate ->
            File(catalogDir, "adr/$candidate").takeIf { it.isDirectory }?.let { readAdrFiles(it) }
        }
    }

    private fun readAdrFiles(dir: File): List<Adr> =
        dir.listFiles { f -> f.extension == "md" }
            ?.sortedBy { it.name }
            ?.map { adrMapper.toAdr(AdrEo().apply { name = it.nameWithoutExtension; content = it.readText() }) }
            ?: emptyList()

    private fun resolveParentDir(file: File): File {
        val resolved = if (file.isAbsolute) file else File(System.getProperty("user.dir"), file.path)
        return resolved.parentFile ?: resolved
    }
}
