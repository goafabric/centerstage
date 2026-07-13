package org.goafabric.centerstage.catalog.logic

import io.quarkus.runtime.StartupEvent
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class CatalogIngestionLogic(
    val catalogLoaderLogic: CatalogLoaderLogic,
    val persistenceMapper: PersistenceMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {
    @Inject lateinit var componentRepo: ComponentEo.Repo
    @Inject lateinit var adrRepo: AdrEo.Repo
    @Inject lateinit var docRepo: DocEo.Repo

    private val log = LoggerFactory.getLogger(javaClass)

    fun onStart(@Observes event: StartupEvent) = ingest()

    @Scheduled(every = "{centerstage.ingestion.interval}")
    @Transactional
    fun ingest() {
        log.info("Starting catalog ingestion ...")
        catalogLoaderLogic.load()

        componentRepo.deleteAll()
        adrRepo.deleteAll()
        docRepo.deleteAll()

        for (eo in catalogLoaderLogic.entries) {
            val entity = persistenceMapper.toComponentEo(eo).also {
                it.id         = UUID.randomUUID().toString()
                it.searchText = buildSearchText(eo)
            }
            componentRepo.save(entity)
        }

        for (eo in catalogLoaderLogic.entries.filter { it.kind == "Component" }) {
            val adrLocation = eo.metadata.annotations["backstage.io/adr-location"] ?: continue
            val adrs = when {
                adrLocation.startsWith("https://github.com")      -> gitHubService.fetchAdrs(adrLocation)
                remoteContentService.isGitLabUrl(adrLocation)     -> gitLabService.fetchAdrs(adrLocation)
                else -> emptyList()
            }
            for (adr in adrs) {
                adrRepo.save(AdrEo().apply {
                    id            = UUID.randomUUID().toString()
                    componentName = eo.metadata.name
                    name          = adr.name
                    content       = adr.content
                    searchText    = "${eo.metadata.name} ${adr.name} ${adr.content}"
                })
            }
        }

        for (eo in catalogLoaderLogic.entries.filter { it.kind == "Component" }) {
            val techDocsRef = eo.metadata.annotations["backstage.io/techdocs-ref"] ?: continue
            val sourcePath  = eo.sourcePath ?: continue
            val docs = when {
                sourcePath.startsWith("https://raw.githubusercontent.com") -> gitHubService.fetchDocs(sourcePath, techDocsRef)
                remoteContentService.isGitLabUrl(sourcePath)               -> gitLabService.fetchDocs(sourcePath, techDocsRef)
                else -> emptyList()
            }
            for (doc in docs) {
                docRepo.save(DocEo().apply {
                    id            = UUID.randomUUID().toString()
                    componentName = eo.metadata.name
                    name          = doc.name
                    content       = doc.content
                    searchText    = "${eo.metadata.name} ${doc.name} ${doc.content}"
                })
            }
        }

        log.info("Catalog ingestion complete: ${componentRepo.count()} entries")
    }

    private fun buildSearchText(eo: org.goafabric.centerstage.catalog.persistence.entity.CatalogEo): String =
        listOfNotNull(
            eo.metadata.name, eo.metadata.description, eo.spec.owner,
            eo.spec.type, eo.spec.lifecycle,
            eo.metadata.tags.joinToString(" "),
            eo.metadata.annotations.values.joinToString(" ")
        ).joinToString(" ")
}
