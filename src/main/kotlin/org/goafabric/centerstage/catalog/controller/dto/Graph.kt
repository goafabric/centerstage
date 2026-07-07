package org.goafabric.centerstage.catalog.controller.dto

data class Graph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

data class GraphNode(
    val id: String,
    val label: String,
    val type: String,       // "component", "resource", "api"
    val kind: String,       // original kind from catalog
    val owner: String?,
    val lifecycle: String?,
    val isFocus: Boolean    // true = the component we navigated from
)

data class GraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val relation: String    // "dependsOn", "dependencyOf", "providesApis"
)
