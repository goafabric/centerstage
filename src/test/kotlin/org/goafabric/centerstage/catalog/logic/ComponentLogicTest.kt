package org.goafabric.centerstage.catalog.logic

import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn

@QuarkusTest
class ComponentLogicTest {

    @Inject lateinit var componentLogic: ComponentLogic
    @Inject lateinit var apiLogic: ApiLogic

    @InjectMock lateinit var componentRepo: ComponentRepository

    private val componentEo = ComponentEo().apply {
        id           = "1"
        name         = "test-service"
        kind         = "Component"
        type         = "service"
        lifecycle    = "production"
        owner        = "team-test"
        description  = "A test service"
        tags         = "java,microservice"
        providesApis = "test-api"
        dependsOn    = ""
        dependencyOf = ""
    }

    private val apiEo = ComponentEo().apply {
        id        = "2"
        name      = "test-api"
        kind      = "API"
        type      = "openapi"
        lifecycle = "production"
        owner     = "team-test"
    }

    @BeforeEach
    fun setup() {
        doReturn(listOf(componentEo)).`when`(componentRepo).findByKind("Component")
        doReturn(listOf(apiEo)).`when`(componentRepo).findByKind("API")
        doReturn(listOf(componentEo)).`when`(componentRepo).findByKindAndName("Component", "test-service")
        doReturn(emptyList<ComponentEo>()).`when`(componentRepo).findByKindAndName("Component", "unknown")
        doReturn(emptyList<ComponentEo>()).`when`(componentRepo).findByKindAndName("API", "unknown")
        doReturn(listOf<ComponentEo>()).`when`(componentRepo).listAll()
    }

    @Test
    fun `getComponents returns only Component entries`() {
        val result = componentLogic.getComponents()
        assertThat(result).hasSize(1)
        assertThat(result.first().name).isEqualTo("test-service")
        assertThat(result.first().type).isEqualTo("service")
        assertThat(result.first().tags).containsExactly("java", "microservice")
    }

    @Test
    fun `getComponent returns component by name`() {
        val result = componentLogic.getComponent("test-service")
        assertThat(result.name).isEqualTo("test-service")
        assertThat(result.owner).isEqualTo("team-test")
    }

    @Test
    fun `getComponent throws for unknown name`() {
        assertThatThrownBy { componentLogic.getComponent("unknown") }
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
