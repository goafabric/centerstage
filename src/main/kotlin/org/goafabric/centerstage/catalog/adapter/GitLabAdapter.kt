package org.goafabric.centerstage.catalog.adapter

import jakarta.ws.rs.Encoded
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "GitLabAdapter")
@Path("/api/v4")
interface GitLabAdapter {

    @GET
    @Path("/projects/{id}/repository/tree")
    @Produces(MediaType.APPLICATION_JSON)
    fun listTree(
        @Encoded @PathParam("id") projectId: String,
        @QueryParam("path") path: String,
        @QueryParam("ref") ref: String,
        @QueryParam("per_page") perPage: Int,
        @HeaderParam("PRIVATE-TOKEN") privateToken: String
    ): List<GitLabTreeItem>

    @GET
    @Path("/projects/{id}/repository/files/{filePath}/raw")
    @Produces(MediaType.TEXT_PLAIN)
    fun getRawFile(
        @Encoded @PathParam("id") projectId: String,
        @Encoded @PathParam("filePath") filePath: String,
        @QueryParam("ref") ref: String,
        @HeaderParam("PRIVATE-TOKEN") privateToken: String
    ): String
}

data class GitLabTreeItem(
    val id: String,
    val name: String,
    val type: String,   // "blob" (file) or "tree" (directory)
    val path: String,
    val mode: String
)
