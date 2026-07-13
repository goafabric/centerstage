package org.goafabric.centerstage.catalog.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.logic.CatalogLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class CatalogController(val catalogLogic: CatalogLogic) {

    @GET
    @Path("/components")
    fun getComponents(): List<Component> = catalogLogic.getComponents()

    @GET
    @Path("/components/{name}")
    fun getComponent(@PathParam("name") name: String): Component = catalogLogic.getComponent(name)
}
