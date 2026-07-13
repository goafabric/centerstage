package org.goafabric.centerstage.catalog.controller.dto

data class SearchResult(
    val type: String,           // "component", "api", "adr", "doc"
    val componentName: String,  // the component this belongs to (for navigation)
    val name: String,           // display name
    val excerpt: String         // short snippet of matched text
)
