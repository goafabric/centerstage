package org.goafabric.centerstage.controller

import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.LoggerFactory
import java.net.URI

@Path("/api/techradar")
@ApplicationScoped
class TechRadarController {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @ConfigProperty(name = "techradar.url", defaultValue = "")
    lateinit var techRadarUrl: String

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getTechRadar(): Response {
        if (techRadarUrl.isBlank()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity("""{"error":"techradar.url not configured"}""").build()
        }
        return try {
            val rawUrl = convertToRawUrl(techRadarUrl)
            log.debug("Fetching tech radar from: $rawUrl")
            val json = URI(rawUrl).toURL().openConnection().apply {
                setRequestProperty("User-Agent", "centerstage")
            }.getInputStream().bufferedReader().readText()
            Response.ok(json).build()
        } catch (e: Exception) {
            log.error("Failed to fetch tech radar: ${e.message}", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("""{"error":"${e.message}"}""").build()
        }
    }

    private fun convertToRawUrl(url: String): String =
        url.replace(
            Regex("https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)"),
            "https://raw.githubusercontent.com/$1/$2/$3/$4"
        )
}