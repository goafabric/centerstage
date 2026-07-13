package org.goafabric.centerstage.catalog.logic

import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.goafabric.centerstage.catalog.persistence.entity.CatalogEo
import org.goafabric.centerstage.catalog.persistence.entity.MetadataEo
import org.goafabric.centerstage.catalog.persistence.entity.SpecEo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn

@QuarkusTest
class CatalogLogicTest {

    @Inject
    lateinit var catalogLogic: CatalogLogic

    @Inject
    lateinit var apiLogic: ApiLogic

    @InjectMock
    lateinit var catalogLoaderLogic: CatalogLoaderLogic

    private val sampleComponent = CatalogEo(
        kind = "Component",
        metadata = MetadataEo(
            name = "test-service",
            description = "A test service",
            tags = listOf("java", "microservice"),
            annotations = emptyMap(),
            links = emptyList()
        ),
        spec = SpecEo(
            type = "service",
            lifecycle = "production",
            owner = "team-test",
            providesApis = listOf("test-api")
        )
    )

    private val sampleApi = CatalogEo(
        kind = "API",
        metadata = MetadataEo(name = "test-api", description = "Test API"),
        spec = SpecEo(type = "openapi", lifecycle = "production", owner = "team-test")
    )

    @BeforeEach
    fun setup() {
        doReturn(mutableListOf(sampleComponent, sampleApi)).`when`(catalogLoaderLogic).entries
    }

    @Test
    fun `getComponents returns only Component entries`() {
        val result = catalogLogic.getComponents()
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("test-service")
        assertThat(result.first().type).isEqualTo("service")
        assertThat(result.first().tags).containsExactly("java", "microservice")
    }

    @Test
    fun `getComponent returns component by name`() {
        val result = catalogLogic.getComponent("test-service")
        assertThat(result.name).isEqualTo("test-service")
        assertThat(result.owner).isEqualTo("team-test")
    }

    @Test
    fun `getComponent throws for unknown name`() {
        assertThatThrownBy { catalogLogic.getComponent("unknown") }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `getApis returns apis for component`() {
        val result = apiLogic.getApis("test-service")
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("test-api")
    }

    @Test
    fun `getApis throws for unknown component`() {
        assertThatThrownBy { apiLogic.getApis("unknown") }
            .isInstanceOf(NoSuchElementException::class.java)
    }
}
