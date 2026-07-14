package org.goafabric.centerstage.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.controller.dto.Graph
import org.goafabric.centerstage.controller.dto.GraphEdge
import org.goafabric.centerstage.controller.dto.GraphNode
import org.goafabric.centerstage.logic.mapper.GraphMapper
import org.goafabric.centerstage.persistence.ComponentRepository
import org.goafabric.centerstage.persistence.entity.ComponentEo

@ApplicationScoped
class GraphLogic {
    @Inject lateinit var componentRepo: ComponentRepository
    @Inject lateinit var graphMapper: GraphMapper

    fun getGraph(componentName: String): Graph {
        val all   = componentRepo.listAll()
        val focus = all.firstOrNull { it.kind == "Component" && it.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val nodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        ensureNode(nodes, graphMapper, focus, isFocus = true)

        for (ref in splitList(focus.dependsOn)) {
            ensureSyntheticNode(nodes, graphMapper, all, ref, if (ref.startsWith("resource:")) "resource" else "component")
            edges.add(makeEdge(edges.size, componentName, nodeId(ref), "dependsOn"))
        }
        for (ref in splitList(focus.dependencyOf)) {
            ensureSyntheticNode(nodes, graphMapper, all, ref, "component")
            edges.add(makeEdge(edges.size, nodeId(ref), componentName, "dependencyOf"))
        }
        for (apiName in splitList(focus.providesApis)) {
            val apiEntry = all.firstOrNull { it.kind == "API" && it.name == apiName }
            if (apiEntry != null) ensureNode(nodes, graphMapper, apiEntry)
            else nodes.getOrPut(apiName) { syntheticNode(apiName, "api", "API") }
            edges.add(makeEdge(edges.size, componentName, apiName, "providesApis"))
        }

        for (entry in all.filter { it.kind == "Component" && it.name != componentName }) {
            val dependsOnFocus    = splitList(entry.dependsOn).any    { nodeId(it) == componentName }
            val dependencyOfFocus = splitList(entry.dependencyOf).any { nodeId(it) == componentName }
            if (dependsOnFocus || dependencyOfFocus) {
                ensureNode(nodes, graphMapper, entry)
                if (dependsOnFocus)    edges.add(makeEdge(edges.size, entry.name, componentName, "dependsOn"))
                if (dependencyOfFocus) edges.add(makeEdge(edges.size, componentName, entry.name, "dependencyOf"))
            }
        }

        return Graph(
            nodes = nodes.values.toList(),
            edges = edges.distinctBy { Triple(it.source, it.target, it.relation) }
                        .mapIndexed { i, e -> e.copy(id = "e$i") }
        )
    }
    private fun nodeId(ref: String) = ref.substringAfter(':')

    private fun splitList(value: String?) = value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    private fun ensureNode(nodes: MutableMap<String, GraphNode>, mapper: GraphMapper, eo: ComponentEo, isFocus: Boolean = false) {
        if (!nodes.containsKey(eo.name)) nodes[eo.name] = mapper.toGraphNode(eo, isFocus)
    }

    private fun ensureSyntheticNode(
        nodes: MutableMap<String, GraphNode>,
        mapper: GraphMapper,
        all: List<ComponentEo>,
        ref: String,
        type: String
    ) {
        val id = nodeId(ref)
        if (!nodes.containsKey(id)) {
            val found = all.firstOrNull { it.name == id }
            if (found != null) ensureNode(nodes, mapper, found)
            else nodes[id] = syntheticNode(id, type, type)
        }
    }

    private fun syntheticNode(id: String, type: String, kind: String) =
        GraphNode(id = id, label = id, type = type, kind = kind, owner = null, lifecycle = null, isFocus = false)

    private fun makeEdge(index: Int, source: String, target: String, relation: String) =
        GraphEdge(id = "e$index", source = source, target = target, relation = relation)

}

