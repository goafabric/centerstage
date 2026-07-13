package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.adapter.GitHubService
import org.goafabric.centerstage.catalog.adapter.GitLabService
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.controller.dto.Api
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.controller.dto.Graph
import org.goafabric.centerstage.catalog.controller.dto.GraphEdge
import org.goafabric.centerstage.catalog.controller.dto.GraphNode
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.CatalogLoader
import org.goafabric.centerstage.catalog.persistence.entity.AdrFileEo
import org.goafabric.centerstage.catalog.persistence.entity.CatalogEo
import java.io.File

@ApplicationScoped
class CatalogLogic(
    val catalogLoader: CatalogLoader,
    val catalogMapper: CatalogMapper,
    val gitHubService: GitHubService,
    val gitLabService: GitLabService,
    val remoteContentService: RemoteContentService
) {

    fun getAllApis(): List<Api> =
        catalogLoader.entries
            .filter { it.kind == "API" }
            .map { catalogMapper.toApi(it) }

    fun getComponents(): List<Component> =
        catalogLoader.entries
            .filter { it.kind == "Component" }
            .map { catalogMapper.toComponent(it) }

    fun getComponent(name: String): Component =
        catalogLoader.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == name }
            ?.let { catalogMapper.toComponent(it) }
            ?: throw NoSuchElementException("Component not found: $name")

    fun getApis(componentName: String): List<Api> {
        val component = catalogLoader.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val apiNames = component.spec.providesApis
        return catalogLoader.entries
            .filter { it.kind == "API" && it.metadata.name in apiNames }
            .map { catalogMapper.toApi(it) }
    }

    fun getGraph(componentName: String): Graph {
        val focus = catalogLoader.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val allEntries = catalogLoader.entries
        val nodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()
        var edgeCounter = 0

        fun nodeId(ref: String): String = ref.substringAfter(':') // strip "component:", "resource:" prefix

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
                // Try to find in catalog
                val found = allEntries.firstOrNull { it.metadata.name == id }
                if (found != null) { ensureNode(found) }
                else nodes[id] = GraphNode(id = id, label = id, type = type, kind = type, owner = null, lifecycle = null, isFocus = false)
            }
        }

        fun addEdge(source: String, target: String, relation: String) {
            edges.add(GraphEdge(id = "e${edgeCounter++}", source = source, target = target, relation = relation))
        }

        // Step 1: seed the focus node
        ensureNode(focus, isFocus = true)

        // Step 2: focus's own outgoing relations — dependsOn, providesApis
        for (ref in focus.spec.dependsOn) {
            val tgt = nodeId(ref)
            ensureSyntheticNode(ref, if (ref.startsWith("resource:")) "resource" else "component")
            addEdge(componentName, tgt, "dependsOn")
        }
        for (ref in focus.spec.dependencyOf) {
            val tgt = nodeId(ref)
            ensureSyntheticNode(ref, "component")
            addEdge(tgt, componentName, "dependencyOf")
        }
        for (apiName in focus.spec.providesApis) {
            val apiEntry = allEntries.firstOrNull { it.kind == "API" && it.metadata.name == apiName }
            if (apiEntry != null) ensureNode(apiEntry)
            else nodes.getOrPut(apiName) { GraphNode(id = apiName, label = apiName, type = "api", kind = "API", owner = null, lifecycle = null, isFocus = false) }
            addEdge(componentName, apiName, "providesApis")
        }

        // Step 3: find components that directly reference the focus in their dependsOn / dependencyOf
        for (entry in allEntries.filter { it.kind == "Component" && it.metadata.name != componentName }) {
            val dependsOnFocus    = entry.spec.dependsOn.any    { nodeId(it) == componentName }
            val dependencyOfFocus = entry.spec.dependencyOf.any { nodeId(it) == componentName }
            if (dependsOnFocus || dependencyOfFocus) {
                ensureNode(entry)
                if (dependsOnFocus)    addEdge(entry.metadata.name, componentName, "dependsOn")
                if (dependencyOfFocus) addEdge(componentName, entry.metadata.name, "dependencyOf")
            }
        }

        // De-duplicate edges (same source+target+relation)
        val uniqueEdges = edges
            .distinctBy { Triple(it.source, it.target, it.relation) }
            .mapIndexed { i, e -> e.copy(id = "e$i") }

        return Graph(nodes = nodes.values.toList(), edges = uniqueEdges)
    }

    fun getAdrs(componentName: String): List<Adr> {
        val component = catalogLoader.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val adrLocation = component.metadata.annotations["backstage.io/adr-location"]

        // Remote GitHub URL — fetch via API
        if (adrLocation != null && adrLocation.startsWith("https://github.com")) {
            return gitHubService.fetchAdrs(adrLocation).map { catalogMapper.toAdr(it) }
        }

        // Remote GitLab URL — fetch via API
        if (adrLocation != null && remoteContentService.isGitLabUrl(adrLocation)) {
            return gitLabService.fetchAdrs(adrLocation).map { catalogMapper.toAdr(it) }
        }

        // Local fallback: only works when catalog is loaded from local filesystem
        val catalogFile = catalogLoader.catalogFile
        if (catalogFile.startsWith("http://") || catalogFile.startsWith("https://")) return emptyList()
        val catalogDir = resolveDir(File(catalogFile))
        val candidates = buildList {
            if (adrLocation != null) {
                add(adrLocation.trimEnd('/').substringAfterLast('/'))
            }
            add(componentName)
        }.distinct()

        for (candidate in candidates) {
            val adrDir = File(catalogDir, "adr/$candidate")
            if (adrDir.exists() && adrDir.isDirectory) return readAdrFiles(adrDir)
        }
        return emptyList()
    }

    fun getDocs(componentName: String): List<TechDoc> {
        val component = catalogLoader.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val techDocsRef = component.metadata.annotations["backstage.io/techdocs-ref"]
            ?: return emptyList()

        val sourcePath = component.sourcePath ?: return emptyList()

        // Remote: sourcePath is a raw.githubusercontent.com URL — fetch docs via GitHub API
        if (sourcePath.startsWith("https://raw.githubusercontent.com")) {
            return gitHubService.fetchDocs(sourcePath, techDocsRef)
        }

        // Remote: sourcePath is a GitLab raw URL — fetch docs via GitLab API
        if (remoteContentService.isGitLabUrl(sourcePath) && sourcePath.contains("/-/raw/")) {
            return gitLabService.fetchDocs(sourcePath, techDocsRef)
        }

        // Local: resolve the docs/ directory relative to the catalog-info.yaml file
        val sourceFile = File(sourcePath)
        val refPath = techDocsRef.removePrefix("dir:").trim()
        val docsRoot = if (refPath == ".") sourceFile.parentFile
                       else File(sourceFile.parentFile, refPath)
        val docsDir = File(docsRoot, "docs")
        if (!docsDir.exists() || !docsDir.isDirectory) return emptyList()

        // Read nav order from mkdocs.yml if present
        val navOrder = parseMkDocsNav(File(docsRoot, "mkdocs.yml"))

        return if (navOrder.isNotEmpty()) {
            navOrder.mapNotNull { (title, filename) ->
                val file = File(docsDir, filename)
                if (file.exists()) TechDoc(name = title, content = file.readText()) else null
            }
        } else {
            docsDir.listFiles { f -> f.extension == "md" }
                ?.sortedBy { it.name }
                ?.map { TechDoc(name = it.nameWithoutExtension, content = it.readText()) }
                ?: emptyList()
        }
    }

    fun getDocsAssetFile(componentName: String, assetPath: String): File? {
        val component = catalogLoader.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: return null

        val techDocsRef = component.metadata.annotations["backstage.io/techdocs-ref"] ?: return null
        val sourcePath = component.sourcePath ?: return null
        // Remote sources don't serve assets from disk
        if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) return null
        val sourceFile = File(sourcePath)
        val refPath = techDocsRef.removePrefix("dir:").trim()
        val docsRoot = if (refPath == ".") sourceFile.parentFile else File(sourceFile.parentFile, refPath)
        val asset = File(docsRoot, "docs/$assetPath").canonicalFile
        // Safety: ensure the resolved path is still inside docsRoot
        return if (asset.startsWith(docsRoot.canonicalFile)) asset else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMkDocsNav(mkdocsFile: File): List<Pair<String, String>> {
        if (!mkdocsFile.exists()) return emptyList()
        return try {
            val yaml = catalogLoader.yamlMapper.readValue(mkdocsFile, Map::class.java)
            val nav = yaml["nav"] as? List<*> ?: return emptyList()
            nav.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val kv = map.entries.firstOrNull() ?: return@mapNotNull null
                val title = kv.key.toString()
                val filename = kv.value.toString()
                title to filename
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readAdrFiles(dir: File): List<Adr> =
        dir.listFiles { f -> f.extension == "md" }
            ?.sortedBy { it.name }
            ?.map { catalogMapper.toAdr(AdrFileEo(name = it.nameWithoutExtension, content = it.readText())) }
            ?: emptyList()

    private fun resolveDir(catalogFile: File): File {
        val f = if (catalogFile.isAbsolute) catalogFile
                else File(System.getProperty("user.dir"), catalogFile.path)
        return f.parentFile ?: f
    }
}
