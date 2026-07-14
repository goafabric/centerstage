package org.goafabric.centerstage.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.controller.dto.Adr
import org.goafabric.centerstage.logic.AdrLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class AdrController(val adrLogic: AdrLogic) {

    @GET
    @Path("/adrs/components")
    fun getComponentNamesWithAdrs(): List<String> = adrLogic.getComponentNamesWithAdrs()

    @GET
    @Path("/components/{name}/adrs")
    fun getAdrs(@PathParam("name") name: String): List<Adr> = adrLogic.getAdrs(name)
}
