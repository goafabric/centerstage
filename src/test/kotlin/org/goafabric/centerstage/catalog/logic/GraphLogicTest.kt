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
class GraphLogicTest {

    @Inject lateinit var graphLogic: GraphLogic

    @InjectMock lateinit var componentRepo: ComponentRepository

    // focus: catalog-service depends on a resource, provides an API, and is depended on by another component
    private val catalogService = ComponentEo().apply {
        id           = "1"
        name         = "catalog-service"
        kind         = "Component"
        type         = "service"
        lifecycle    = "production"
        owner        = "team-blue"
        providesApis = "catalog-api"
        dependsOn    = "resource:catalog-db"
        dependencyOf = "component:api-gateway"
    }

    private val catalogApi = ComponentEo().apply {
        id   = "2"
        name = "catalog-api"
        kind = "API"
        type = "openapi"
        owner = "team-blue"
    }

    private val catalogDb = ComponentEo().apply {
        id   = "3"
        name = "catalog-db"
        kind = "Resource"
        type = "database"
        owner = "devops"
    }

    // api-gateway references catalog-service in its dependsOn
    private val apiGateway = ComponentEo().apply {
        id        = "4"
        name      = "api-gateway"
        kind      = "Component"
        type      = "service"
        lifecycle = "production"
        owner     = "team-blue"
        dependsOn = "component:catalog-service"
    }

    @BeforeEach
    fun setup() {
        doReturn(listOf(catalogService, catalogApi, catalogDb, apiGateway))
            .`when`(componentRepo).listAll()
    }

    @Test
    fun `getGraph returns focus node`() {
        val graph = graphLogic.getGraph("catalog-service")
        val focus = graph.nodes.firstOrNull { it.isFocus }
        assertThat(focus).isNotNull
        assertThat(focus!!.id).isEqualTo("catalog-service")
        assertThat(focus.type).isEqualTo("component")
    }

    @Test
    fun `getGraph includes dependsOn resource node and edge`() {
        val graph = graphLogic.getGraph("catalog-service")
        assertThat(graph.nodes.map { it.id }).contains("catalog-db")
        assertThat(graph.nodes.first { it.id == "catalog-db" }.type).isEqualTo("resource")
        assertThat(graph.edges).anyMatch { it.source == "catalog-service" && it.target == "catalog-db" && it.relation == "dependsOn" }
    }

    @Test
    fun `getGraph includes providesApis API node and edge`() {
        val graph = graphLogic.getGraph("catalog-service")
        assertThat(graph.nodes.map { it.id }).contains("catalog-api")
        assertThat(graph.nodes.first { it.id == "catalog-api" }.type).isEqualTo("api")
        assertThat(graph.edges).anyMatch { it.source == "catalog-service" && it.target == "catalog-api" && it.relation == "providesApis" }
    }

    @Test
    fun `getGraph includes reverse dependsOn neighbour`() {
        val graph = graphLogic.getGraph("catalog-service")
        assertThat(graph.nodes.map { it.id }).contains("api-gateway")
        assertThat(graph.edges).anyMatch { it.source == "api-gateway" && it.target == "catalog-service" && it.relation == "dependsOn" }
    }

    @Test
    fun `getGraph throws for unknown component`() {
        assertThatThrownBy { graphLogic.getGraph("does-not-exist") }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `getGraph handles null kind on neighbour entity without NPE`() {
        val nullKindEntry = ComponentEo().apply {
            id        = "5"
            name      = "mystery-service"
            kind      = ""   // empty kind — mimics the condition that caused the original NPE
            type      = null
            owner     = null
            dependsOn = "component:catalog-service"
        }
        doReturn(listOf(catalogService, catalogApi, catalogDb, nullKindEntry))
            .`when`(componentRepo).listAll()

        val graph = graphLogic.getGraph("catalog-service")
        assertThat(graph.nodes.map { it.id }).contains("catalog-service")
    }
}
