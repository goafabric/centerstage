package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.controller.dto.Graph
import org.goafabric.centerstage.catalog.controller.dto.GraphEdge
import org.goafabric.centerstage.catalog.controller.dto.GraphNode
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo

@ApplicationScoped
class GraphLogic {
    @Inject lateinit var componentRepo: ComponentRepository

    fun getGraph(componentName: String): Graph {
        val all = componentRepo.listAll()
        val focus = all.firstOrNull { it.kind == "Component" && it.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val nodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        var edgeCounter = 0

        fun nodeId(ref: String) = ref.substringAfter(':')

        fun ensureNode(eo: ComponentEo, isFocus: Boolean = false) {
            if (!nodes.containsKey(eo.name)) {
                nodes[eo.name] = GraphNode(
                    id        = eo.name,
                    label     = eo.name,
                    type      = when (eo.kind) { "Component" -> "component"; "Resource" -> "resource"; "API" -> "api"; else -> "other" },
                    kind      = eo.kind,
                    owner     = eo.owner,
                    lifecycle = eo.lifecycle,
                    isFocus   = isFocus
                )
            }
        }

        fun ensureSyntheticNode(ref: String, type: String) {
            val id = nodeId(ref)
            if (!nodes.containsKey(id)) {
                val found = all.firstOrNull { it.name == id }
                if (found != null) ensureNode(found)
                else nodes[id] = GraphNode(id = id, label = id, type = type, kind = type, owner = null, lifecycle = null, isFocus = false)
            }
        }

        fun addEdge(source: String, target: String, relation: String) =
            edges.add(GraphEdge(id = "e${edgeCounter++}", source = source, target = target, relation = relation))

        fun splitList(value: String?) = value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

        ensureNode(focus, isFocus = true)

        for (ref in splitList(focus.dependsOn)) {
            ensureSyntheticNode(ref, if (ref.startsWith("resource:")) "resource" else "component")
            addEdge(componentName, nodeId(ref), "dependsOn")
        }
        for (ref in splitList(focus.dependencyOf)) {
            ensureSyntheticNode(ref, "component")
            addEdge(nodeId(ref), componentName, "dependencyOf")
        }
        for (apiName in splitList(focus.providesApis)) {
            val apiEntry = all.firstOrNull { it.kind == "API" && it.name == apiName }
            if (apiEntry != null) ensureNode(apiEntry)
            else nodes.getOrPut(apiName) { GraphNode(id = apiName, label = apiName, type = "api", kind = "API", owner = null, lifecycle = null, isFocus = false) }
            addEdge(componentName, apiName, "providesApis")
        }

        for (entry in all.filter { it.kind == "Component" && it.name != componentName }) {
            val dependsOnFocus    = splitList(entry.dependsOn).any    { nodeId(it) == componentName }
            val dependencyOfFocus = splitList(entry.dependencyOf).any { nodeId(it) == componentName }
            if (dependsOnFocus || dependencyOfFocus) {
                ensureNode(entry)
                if (dependsOnFocus)    addEdge(entry.name, componentName, "dependsOn")
                if (dependencyOfFocus) addEdge(componentName, entry.name, "dependencyOf")
            }
        }

        return Graph(
            nodes = nodes.values.toList(),
            edges = edges.distinctBy { Triple(it.source, it.target, it.relation) }
                        .mapIndexed { i, e -> e.copy(id = "e$i") }
        )
    }
}
