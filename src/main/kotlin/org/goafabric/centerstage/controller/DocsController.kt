package org.goafabric.centerstage.controller

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.goafabric.centerstage.controller.dto.TechDoc
import org.goafabric.centerstage.logic.DocsLogic

@Path("/api/catalog")
@Produces(MediaType.APPLICATION_JSON)
class DocsController(val docsLogic: DocsLogic) {

    @GET
    @Path("/components/{name}/docs")
    fun getDocs(@PathParam("name") name: String): List<TechDoc> = docsLogic.getDocs(name)

    @GET
    @Path("/components/{name}/docs/assets/{assetPath: .+}")
    @Produces(MediaType.WILDCARD)
    fun getDocAsset(@PathParam("name") name: String, @PathParam("assetPath") assetPath: String): Response {
        val file = docsLogic.getDocsAssetFile(name, assetPath)
            ?: return Response.status(Response.Status.NOT_FOUND).build()
        val mimeType = when (file.extension.lowercase()) {
            "png"         -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif"         -> "image/gif"
            "svg"         -> "image/svg+xml"
            "webp"        -> "image/webp"
            else          -> "application/octet-stream"
        }
        return Response.ok(file.readBytes(), mimeType).build()
    }
}
