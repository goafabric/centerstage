package org.goafabric.centerstage.catalog.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.catalog.controller.dto.Graph
import org.goafabric.centerstage.catalog.logic.GraphLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class GraphController(val graphLogic: GraphLogic) {

    @GET
    @Path("/components/{name}/graph")
    fun getGraph(@PathParam("name") name: String): Graph = graphLogic.getGraph(name)
}
