package org.goafabric.centerstage.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.controller.dto.Graph
import org.goafabric.centerstage.logic.GraphLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class GraphController(val graphLogic: GraphLogic) {

    @GET
    @Path("/components/{name}/graph")
    fun getGraph(@PathParam("name") name: String): Graph = graphLogic.getGraph(name)
}
