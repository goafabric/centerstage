package org.goafabric.centerstage.catalog.logic

import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.transaction.Transactional
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.persistence.AdrRepository
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.DocRepository
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationScoped
class CatalogIngestionLogic(
    val catalogLoaderLogic: CatalogLoaderLogic,
    val componentRepo: ComponentRepository,
    val adrRepo: AdrRepository,
    val docRepo: DocRepository,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun onStart(@Observes event: StartupEvent) = ingest()

    // delayed = same as interval so the scheduler never double-fires at startup
    @Scheduled(every = "{centerstage.ingestion.interval}", delayed = "{centerstage.ingestion.interval}")
    @Transactional
    fun ingest() {
        log.info("Starting catalog ingestion ...")
        catalogLoaderLogic.load()
        deleteAll()
        persistComponents()
        ingestAdrs()
        ingestDocs()
        log.info("Catalog ingestion complete: ${componentRepo.count()} entries")
    }

    private fun deleteAll() {
        componentRepo.deleteAll()
        adrRepo.deleteAll()
        docRepo.deleteAll()
    }

    private fun persistComponents() =
        catalogLoaderLogic.entries.forEach { componentRepo.save(it) }

    private fun ingestAdrs() =
        componentsWithAnnotation("backstage.io/adr-location").forEach { component ->
            fetchAdrs(component.annotation("backstage.io/adr-location")!!)
                .forEach { adr -> adrRepo.save(adr.withComponent(component)) }
        }

    private fun ingestDocs() =
        componentsWithAnnotation("backstage.io/techdocs-ref").forEach { component ->
            val techDocsRef = component.annotation("backstage.io/techdocs-ref")!!
            val sourcePath  = component.sourcePath ?: return@forEach
            fetchDocs(sourcePath, techDocsRef)
                .forEach { doc -> docRepo.save(toDocEo(doc, component.name)) }
        }

    private fun componentsWithAnnotation(key: String) =
        catalogLoaderLogic.entries.filter { it.kind == "Component" && it.annotation(key) != null }

    private fun fetchAdrs(adrLocation: String): List<AdrEo> = when {
        adrLocation.startsWith("https://github.com")  -> gitHubService.fetchAdrs(adrLocation)
        remoteContentService.isGitLabUrl(adrLocation) -> gitLabService.fetchAdrs(adrLocation)
        else -> emptyList()
    }

    private fun fetchDocs(sourcePath: String, techDocsRef: String) = when {
        sourcePath.startsWith("https://raw.githubusercontent.com") -> gitHubService.fetchDocs(sourcePath, techDocsRef)
        remoteContentService.isGitLabUrl(sourcePath)               -> gitLabService.fetchDocs(sourcePath, techDocsRef)
        else -> emptyList()
    }

    private fun AdrEo.withComponent(component: ComponentEo) = this.apply {
        id            = UUID.randomUUID().toString()
        componentName = component.name
        searchText    = "${component.name} $name $content"
    }

    private fun toDocEo(doc: org.goafabric.centerstage.catalog.controller.dto.TechDoc, componentName: String) =
        DocEo().apply {
            id                    = UUID.randomUUID().toString()
            this.componentName    = componentName
            name                  = doc.name
            content               = doc.content
            searchText            = "$componentName ${doc.name} ${doc.content}"
        }
}
