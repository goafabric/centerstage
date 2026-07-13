package org.goafabric.centerstage.catalog.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.logic.AdrLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class AdrController(val adrLogic: AdrLogic) {

    @GET
    @Path("/components/{name}/adrs")
    fun getAdrs(@PathParam("name") name: String): List<Adr> = adrLogic.getAdrs(name)
}
