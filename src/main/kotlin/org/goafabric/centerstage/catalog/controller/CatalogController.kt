package org.goafabric.centerstage.catalog.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.controller.dto.Api
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.controller.dto.Graph
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.logic.CatalogLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class CatalogController(val catalogLogic: CatalogLogic) {

    @GET
    @Path("/apis")
    fun getAllApis(): List<Api> = catalogLogic.getAllApis()

    @GET
    @Path("/components")
    fun getComponents(): List<Component> = catalogLogic.getComponents()

    @GET
    @Path("/components/{name}")
    fun getComponent(@PathParam("name") name: String): Component = catalogLogic.getComponent(name)

    @GET
    @Path("/components/{name}/apis")
    fun getApis(@PathParam("name") name: String): List<Api> = catalogLogic.getApis(name)

    @GET
    @Path("/components/{name}/adrs")
    fun getAdrs(@PathParam("name") name: String): List<Adr> = catalogLogic.getAdrs(name)

    @GET
    @Path("/components/{name}/graph")
    fun getGraph(@PathParam("name") name: String): Graph = catalogLogic.getGraph(name)

    @GET
    @Path("/components/{name}/docs")
    fun getDocs(@PathParam("name") name: String): List<TechDoc> = catalogLogic.getDocs(name)

    @GET
    @Path("/components/{name}/docs/assets/{assetPath: .+}")
    @Produces(MediaType.WILDCARD)
    fun getDocAsset(@PathParam("name") name: String, @PathParam("assetPath") assetPath: String): Response {
        val file = catalogLogic.getDocsAssetFile(name, assetPath)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        if (!file.exists()) return Response.status(Response.Status.NOT_FOUND).build()
        val mimeType = when (file.extension.lowercase()) {
            "png"  -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif"  -> "image/gif"
            "svg"  -> "image/svg+xml"
            "webp" -> "image/webp"
            else   -> "application/octet-stream"
        }
        return Response.ok(file.readBytes(), mimeType).build()
    }
}
