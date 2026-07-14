package org.goafabric.centerstage.controller

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusIntegrationTest
class ComponentControllerIT {

    @Test
    fun `GET components returns list with person-service`() {
        val components = given()
            .`when`().get("/api/catalog/components")
            .then()
            .statusCode(200)
            .extract().jsonPath().getList<Map<String, Any>>(".")

        assertThat(components).isNotEmpty
        assertThat(components.map { it["name"] }).contains("person-service")
    }

    @Test
    fun `GET component by name returns component`() {
        val component = given()
            .`when`().get("/api/catalog/components/person-service")
            .then()
            .statusCode(200)
            .extract().jsonPath().getMap<String, Any>(".")

        assertThat(component["name"]).isEqualTo("person-service")
        assertThat(component["owner"]).isEqualTo("team-alpha")
        assertThat(component["type"]).isEqualTo("service")
    }

    @Test
    fun `GET component by unknown name returns 404`() {
        given()
            .`when`().get("/api/catalog/components/does-not-exist")
            .then()
            .statusCode(404)
    }

    @Test
    fun `GET apis for component returns list`() {
        val apis = given()
            .`when`().get("/api/catalog/components/person-service/apis")
            .then()
            .statusCode(200)
            .extract().jsonPath().getList<Map<String, Any>>(".")

        assertThat(apis).isNotEmpty
        assertThat(apis.map { it["name"] }).contains("person-api")
    }

    @Test
    fun `GET adrs for component returns list`() {
        given()
            .`when`().get("/api/catalog/components/person-service/adrs")
            .then()
            .statusCode(200)
    }

    @Test
    fun `GET graph for component returns nodes and edges`() {
        val graph = given()
            .`when`().get("/api/catalog/components/person-service/graph")
            .then()
            .statusCode(200)
            .extract().jsonPath()

        val nodeIds = graph.getList<Map<String, Any>>("nodes").map { it["id"] }
        assertThat(nodeIds).contains("person-service", "person-api", "person-db")

        val edges = graph.getList<Map<String, Any>>("edges")
        assertThat(edges).anyMatch { it["source"] == "person-service" && it["target"] == "person-api" && it["relation"] == "providesApis" }
        assertThat(edges).anyMatch { it["source"] == "person-service" && it["target"] == "person-db"  && it["relation"] == "dependsOn" }
    }
}
