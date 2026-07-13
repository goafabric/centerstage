package org.goafabric.centerstage.catalog.persistence

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class CatalogRepositoryIT {

    @Inject lateinit var componentRepo: ComponentEo.Repo
    @Inject lateinit var adrRepo: AdrEo.Repo
    @Inject lateinit var docRepo: DocEo.Repo
    @Inject lateinit var persistenceMapper: PersistenceMapper

    @BeforeEach
    @Transactional
    fun setup() {
        adrRepo.deleteAll()
        docRepo.deleteAll()
        componentRepo.deleteAll()

        componentRepo.save(ComponentEo().apply {
            id           = UUID.randomUUID().toString()
            name         = "person-service"
            kind         = "Component"
            type         = "service"
            lifecycle    = "production"
            owner        = "team-alpha"
            description  = "Manages persons"
            tags         = "java,microservice"
            annotations  = "backstage.io/techdocs-ref=dir:."
            providesApis = "person-api"
            dependsOn    = ""
            dependencyOf = ""
            searchText   = "person-service Manages persons team-alpha service production java microservice"
        })
        componentRepo.save(ComponentEo().apply {
            id            = UUID.randomUUID().toString()
            name          = "person-api"
            kind          = "API"
            type          = "openapi"
            lifecycle     = "production"
            owner         = "team-alpha"
            description   = "Person REST API"
            definitionUrl = "https://example.com/openapi.json"
            searchText    = "person-api Person REST API team-alpha openapi production"
        })

        adrRepo.save(AdrEo().apply {
            id            = UUID.randomUUID().toString()
            componentName = "person-service"
            name          = "0001-use-rest"
            content       = "We decided to use REST because it is simple"
            searchText    = "person-service 0001-use-rest decided REST"
        })

        docRepo.save(DocEo().apply {
            id            = UUID.randomUUID().toString()
            componentName = "person-service"
            name          = "overview"
            content       = "Person service documentation overview"
            searchText    = "person-service overview documentation"
        })
    }

    @Test
    fun `findByKind returns components`() {
        val result = componentRepo.findByKind("Component")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("person-service")
    }

    @Test
    fun `findByKind returns APIs with definitionUrl`() {
        val result = componentRepo.findByKind("API")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("person-api")
        assertThat(result.first().definitionUrl).isEqualTo("https://example.com/openapi.json")
    }

    @Test
    fun `findByKindAndName returns matching entry`() {
        val result = componentRepo.findByKindAndName("Component", "person-service")
        assertThat(result).hasSize(1)
        assertThat(result.first().owner).isEqualTo("team-alpha")
    }

    @Test
    fun `findByKindAndName returns empty for unknown`() {
        assertThat(componentRepo.findByKindAndName("Component", "does-not-exist")).isEmpty()
    }

    @Test
    fun `search finds component by description`() {
        val result = componentRepo.search("%Manages persons%")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("person-service")
    }

    @Test
    fun `search returns empty for no match`() {
        assertThat(componentRepo.search("%xyz-no-match%")).isEmpty()
    }

    @Test
    fun `persistenceMapper maps ComponentEo to CatalogEo correctly`() {
        val eo = componentRepo.findByKindAndName("Component", "person-service").first()
        val catalogEo = persistenceMapper.toCatalogEo(eo)
        assertThat(catalogEo.metadata.name).isEqualTo("person-service")
        assertThat(catalogEo.metadata.tags).containsExactly("java", "microservice")
        assertThat(catalogEo.spec.owner).isEqualTo("team-alpha")
        assertThat(catalogEo.spec.providesApis).containsExactly("person-api")
    }

    @Test
    fun `persistenceMapper maps API definitionUrl to DefinitionEo`() {
        val eo = componentRepo.findByKindAndName("API", "person-api").first()
        val catalogEo = persistenceMapper.toCatalogEo(eo)
        assertThat(catalogEo.spec.definition?.text).isEqualTo("https://example.com/openapi.json")
    }

    @Test
    fun `findByComponentName returns ADRs`() {
        val result = adrRepo.findByComponentName("person-service")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("0001-use-rest")
        assertThat(result.first().content).contains("REST")
    }

    @Test
    fun `adrRepo search finds by content`() {
        val result = adrRepo.search("%decided%")
        assertThat(result).hasSize(1)
    }

    @Test
    fun `findByComponentName returns docs`() {
        val result = docRepo.findByComponentName("person-service")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("overview")
    }

    @Test
    fun `docRepo search finds by content`() {
        val result = docRepo.search("%documentation%")
        assertThat(result).hasSize(1)
    }
}
