package org.goafabric.centerstage.controller.dto

data class Component(
    val name: String,
    val owner: String?,
    val type: String?,
    val lifecycle: String?,
    val description: String?,
    val tags: List<String>,
    val links: List<Link>,
    val annotations: Map<String, String>,
    val providesApis: List<String>
)

data class Link(
    val url: String,
    val title: String?
)
