package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.controller.dto.Graph
import org.goafabric.centerstage.catalog.controller.dto.GraphEdge
import org.goafabric.centerstage.catalog.controller.dto.GraphNode
import org.goafabric.centerstage.catalog.persistence.entity.CatalogEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper

@ApplicationScoped
class GraphLogic(val persistenceMapper: PersistenceMapper) {
    @Inject lateinit var componentRepo: ComponentEo.Repo

    fun getGraph(componentName: String): Graph {
        val allEntries = componentRepo.listAll().map { persistenceMapper.toCatalogEo(it) }

        val focus = allEntries.firstOrNull { it.kind == "Component" && it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val nodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        var edgeCounter = 0

        fun nodeId(ref: String): String = ref.substringAfter(':')

        fun ensureNode(eo: CatalogEo, isFocus: Boolean = false) {
            val id = eo.metadata.name
            if (!nodes.containsKey(id)) {
                nodes[id] = GraphNode(
                    id        = id,
                    label     = id,
                    type      = when (eo.kind) { "Component" -> "component"; "Resource" -> "resource"; "API" -> "api"; else -> "other" },
                    kind      = eo.kind,
                    owner     = eo.spec.owner,
                    lifecycle = eo.spec.lifecycle,
                    isFocus   = isFocus
                )
            }
        }

        fun ensureSyntheticNode(ref: String, type: String) {
            val id = nodeId(ref)
            if (!nodes.containsKey(id)) {
                val found = allEntries.firstOrNull { it.metadata.name == id }
                if (found != null) ensureNode(found)
                else nodes[id] = GraphNode(id = id, label = id, type = type, kind = type, owner = null, lifecycle = null, isFocus = false)
            }
        }

        fun addEdge(source: String, target: String, relation: String) =
            edges.add(GraphEdge(id = "e${edgeCounter++}", source = source, target = target, relation = relation))

        ensureNode(focus, isFocus = true)

        for (ref in focus.spec.dependsOn) {
            ensureSyntheticNode(ref, if (ref.startsWith("resource:")) "resource" else "component")
            addEdge(componentName, nodeId(ref), "dependsOn")
        }
        for (ref in focus.spec.dependencyOf) {
            ensureSyntheticNode(ref, "component")
            addEdge(nodeId(ref), componentName, "dependencyOf")
        }
        for (apiName in focus.spec.providesApis) {
            val apiEntry = allEntries.firstOrNull { it.kind == "API" && it.metadata.name == apiName }
            if (apiEntry != null) ensureNode(apiEntry)
            else nodes.getOrPut(apiName) { GraphNode(id = apiName, label = apiName, type = "api", kind = "API", owner = null, lifecycle = null, isFocus = false) }
            addEdge(componentName, apiName, "providesApis")
        }

        for (entry in allEntries.filter { it.kind == "Component" && it.metadata.name != componentName }) {
            val dependsOnFocus    = entry.spec.dependsOn.any    { nodeId(it) == componentName }
            val dependencyOfFocus = entry.spec.dependencyOf.any { nodeId(it) == componentName }
            if (dependsOnFocus || dependencyOfFocus) {
                ensureNode(entry)
                if (dependsOnFocus)    addEdge(entry.metadata.name, componentName, "dependsOn")
                if (dependencyOfFocus) addEdge(componentName, entry.metadata.name, "dependencyOf")
            }
        }

        return Graph(
            nodes = nodes.values.toList(),
            edges = edges.distinctBy { Triple(it.source, it.target, it.relation) }
                        .mapIndexed { i, e -> e.copy(id = "e$i") }
        )
    }
}
