package org.goafabric.centerstage.catalog.controller

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test

@QuarkusTest
class CatalogControllerIT {

    @Test
    fun `GET components returns list`() {
        val components = given()
            .`when`().get("/api/catalog/components")
            .then()
            .statusCode(200)
            .extract().jsonPath().getList<Map<String, Any>>(".")

        assertThat(components).isNotEmpty
    }

    @Test
    fun `GET component by name returns component`() {
        // First get the list to find a valid name
        val components = given()
            .`when`().get("/api/catalog/components")
            .then()
            .statusCode(200)
            .extract().jsonPath().getList<Map<String, Any>>(".")

        assertThat(components).isNotEmpty
        val firstName = components.first()["name"] as String

        val component = given()
            .`when`().get("/api/catalog/components/$firstName")
            .then()
            .statusCode(200)
            .extract().jsonPath().getMap<String, Any>(".")

        assertThat(component["name"]).isEqualTo(firstName)
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
        val components = given()
            .`when`().get("/api/catalog/components")
            .then()
            .statusCode(200)
            .extract().jsonPath().getList<Map<String, Any>>(".")

        assertThat(components).isNotEmpty
        val firstName = components.first()["name"] as String

        given()
            .`when`().get("/api/catalog/components/$firstName/apis")
            .then()
            .statusCode(200)
    }

    @Test
    fun `GET adrs for component returns list`() {
        val components = given()
            .`when`().get("/api/catalog/components")
            .then()
            .statusCode(200)
            .extract().jsonPath().getList<Map<String, Any>>(".")

        assertThat(components).isNotEmpty
        val firstName = components.first()["name"] as String

        given()
            .`when`().get("/api/catalog/components/$firstName/adrs")
            .then()
            .statusCode(200)
    }
}
