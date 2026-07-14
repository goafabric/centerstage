package org.goafabric.centerstage.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.goafabric.centerstage.controller.dto.Api
import org.goafabric.centerstage.logic.ApiLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class ApiController(val apiLogic: ApiLogic) {

    @GET
    @Path("/apis")
    fun getAllApis(): List<Api> = apiLogic.getAllApis()

    @GET
    @Path("/apis/{name}/spec")
    @Produces(MediaType.WILDCARD)
    fun getApiSpecByName(@PathParam("name") name: String): Response {
        val spec = apiLogic.getApiSpecByName(name)
        return Response.ok(spec, if (spec.trimStart().startsWith("{")) MediaType.APPLICATION_JSON else "application/yaml").build()
    }

    @GET
    @Path("/components/{name}/apis")
    fun getApis(@PathParam("name") name: String): List<Api> = apiLogic.getApis(name)

    @GET
    @Path("/components/{name}/api-spec")
    @Produces(MediaType.WILDCARD)
    fun getApiSpec(@PathParam("name") name: String): Response {
        val spec = apiLogic.getApiSpec(name)
        return Response.ok(spec, if (spec.trimStart().startsWith("{")) MediaType.APPLICATION_JSON else "application/yaml").build()
    }
}
