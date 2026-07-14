package org.goafabric.centerstage.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.controller.dto.Component
import org.goafabric.centerstage.logic.ComponentLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class ComponentController(val componentLogic: ComponentLogic) {

    @GET
    @Path("/components")
    fun getComponents(): List<Component> = componentLogic.getComponents()

    @GET
    @Path("/components/{name}")
    fun getComponent(@PathParam("name") name: String): Component = componentLogic.getComponent(name)
}
