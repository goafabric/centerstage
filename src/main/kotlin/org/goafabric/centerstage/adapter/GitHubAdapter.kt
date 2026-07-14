package org.goafabric.centerstage.adapter

import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient

@RegisterRestClient(configKey = "GitHubAdapter")
@Path("/")
interface GitHubAdapter {

    @GET
    @Path("/repos/{owner}/{repo}/contents/{path}")
    @Produces(MediaType.APPLICATION_JSON)
    fun listContents(
        @PathParam("owner") owner: String,
        @PathParam("repo") repo: String,
        @PathParam("path") path: String,
        @QueryParam("ref") ref: String,
        @HeaderParam("User-Agent") userAgent: String
    ): List<GitHubFile>
}

data class GitHubFile(
    val name: String,
    val type: String,                                          // "file" or "dir"
    @com.fasterxml.jackson.annotation.JsonProperty("download_url")
    val downloadUrl: String?
)
