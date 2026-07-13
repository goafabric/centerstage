package org.goafabric.centerstage.catalog.persistence

import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.AdrRepository
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.DocRepository
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class CatalogRepositoryIT {

    @Inject lateinit var componentRepo: ComponentRepository
    @Inject lateinit var adrRepo: AdrRepository
    @Inject lateinit var docRepo: DocRepository
    @Inject lateinit var catalogMapper: CatalogMapper

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
        assertThat(componentRepo.search("%Manages persons%")).hasSize(1)
    }

    @Test
    fun `search returns empty for no match`() {
        assertThat(componentRepo.search("%xyz-no-match%")).isEmpty()
    }

    @Test
    fun `catalogMapper toComponent splits comma fields correctly`() {
        val eo = componentRepo.findByKindAndName("Component", "person-service").first()
        val dto = catalogMapper.toComponent(eo)
        assertThat(dto.name).isEqualTo("person-service")
        assertThat(dto.tags).containsExactly("java", "microservice")
        assertThat(dto.owner).isEqualTo("team-alpha")
        assertThat(dto.providesApis).containsExactly("person-api")
    }

    @Test
    fun `catalogMapper toApi maps definitionUrl`() {
        val eo = componentRepo.findByKind("API").first()
        val dto = catalogMapper.toApi(eo)
        assertThat(dto.name).isEqualTo("person-api")
        assertThat(dto.definitionUrl).isEqualTo("https://example.com/openapi.json")
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
        assertThat(adrRepo.search("%decided%")).hasSize(1)
    }

    @Test
    fun `findByComponentName returns docs`() {
        val result = docRepo.findByComponentName("person-service")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("overview")
    }

    @Test
    fun `docRepo search finds by content`() {
        assertThat(docRepo.search("%documentation%")).hasSize(1)
    }
}
