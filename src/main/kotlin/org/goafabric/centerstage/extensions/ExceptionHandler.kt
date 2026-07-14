package org.goafabric.centerstage.extensions

import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import org.slf4j.LoggerFactory

@Provider
class ExceptionHandler : ExceptionMapper<Exception> {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun toResponse(e: Exception): Response {
        if (e is WebApplicationException) {
            return e.response
        }
        val status = when (e) {
            is IllegalArgumentException -> Response.Status.PRECONDITION_FAILED
            is IllegalStateException    -> Response.Status.PRECONDITION_FAILED
            is NoSuchElementException   -> Response.Status.NOT_FOUND
            else                        -> Response.Status.INTERNAL_SERVER_ERROR
        }
        log.error(e.message, e)
        return Response.status(status).entity("An error occurred: ${e.message}").build()
    }
}
