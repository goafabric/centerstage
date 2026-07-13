package org.goafabric.centerstage.catalog.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.goafabric.centerstage.catalog.controller.dto.SearchResult
import org.goafabric.centerstage.catalog.logic.SearchLogic

@Path("/api/catalog/search")
@Produces(MediaType.APPLICATION_JSON)
class SearchController(val searchLogic: SearchLogic) {

    @GET
    fun search(@QueryParam("q") query: String): List<SearchResult> =
        searchLogic.search(query)
}
