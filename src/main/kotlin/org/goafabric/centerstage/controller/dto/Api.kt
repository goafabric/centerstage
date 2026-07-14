package org.goafabric.centerstage.controller.dto

data class Api(
    val name: String,
    val type: String?,
    val lifecycle: String?,
    val description: String?,
    val definitionUrl: String?
)
