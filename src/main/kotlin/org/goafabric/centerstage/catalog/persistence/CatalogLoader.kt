package org.goafabric.centerstage.catalog.persistence

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.persistence.entity.CatalogEo
import org.goafabric.centerstage.catalog.persistence.entity.DefinitionEo
import org.goafabric.centerstage.catalog.persistence.entity.LinkEo
import org.goafabric.centerstage.catalog.persistence.entity.MetadataEo
import org.goafabric.centerstage.catalog.persistence.entity.SpecEo
import org.slf4j.LoggerFactory
import java.io.File

@ApplicationScoped
class CatalogLoader(
    val remoteContentService: RemoteContentService
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @ConfigProperty(name = "centerstage.catalog.file", defaultValue = "doc/catalog/entities-local.yaml")
    lateinit var catalogFile: String

    val entries: MutableList<CatalogEo> = mutableListOf()

    val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun onStart(@Observes event: StartupEvent) {
        load()
    }

    fun load() {
        entries.clear()
        log.info("Loading catalog from: $catalogFile")

        val entryContent = readContent(catalogFile)
        if (entryContent == null) {
            log.warn("Catalog file not found or could not be fetched: $catalogFile")
            return
        }

        val locationDoc = parseFirstDocument(entryContent)
        val targets = extractTargets(locationDoc)

        for (target in targets) {
            val resolvedTarget = resolveTarget(catalogFile, target)
            // Normalise to raw URL so sourcePath and $text resolution always use the fetchable form
            val normalizedTarget = if (resolvedTarget.startsWith("http://") || resolvedTarget.startsWith("https://"))
                remoteContentService.toRawUrl(resolvedTarget) else resolvedTarget
            val content = readContent(normalizedTarget)
            if (content == null) {
                log.warn("Target not found: $normalizedTarget")
                continue
            }
            val docs = parseAllDocuments(content)
            for (doc in docs) {
                val eo = mapToEo(doc, normalizedTarget)?.copy(sourcePath = normalizedTarget) ?: continue
                entries.add(eo)
                log.debug("Loaded ${eo.kind}: ${eo.metadata.name}")
            }
        }
        log.info("Catalog loaded: ${entries.size} entries (${entries.count { it.kind == "Component" }} components)")
    }

    /** Reads content from a local file path or an HTTP(S) URL via RemoteContentService. */
    private fun readContent(path: String): String? {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            remoteContentService.fetchTextOrNull(remoteContentService.toRawUrl(path))
        } else {
            val file = resolveLocalFile(path)
            if (file.exists()) file.readText() else null
        }
    }

    /** Resolves a target (which may be a URL or relative path) against the base catalog file location. */
    private fun resolveTarget(base: String, target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        return if (base.startsWith("http://") || base.startsWith("https://")) {
            val baseDir = base.substringBeforeLast("/")
            "$baseDir/${target.removePrefix("./")}"
        } else {
            val baseDir = resolveLocalFile(base).parentFile
            File(baseDir, target.removePrefix("./")).path
        }
    }

    /**
     * Resolves a definition $text value:
     * - If it's already an absolute URL, convert blob → raw if needed.
     * - If sourceLocation is a GitLab API raw URL (…/repository/files/{encodedPath}/raw?ref={ref}),
     *   re-encode the relative path and build a new API URL for it.
     * - If it's a relative path and sourceLocation is a GitHub/other URL, resolve relative to directory.
     * - If it's a relative path and sourceLocation is a local file, keep as-is.
     */
    private fun resolveDefinitionUrl(text: String, sourceLocation: String): String {
        if (text.startsWith("http://") || text.startsWith("https://")) return remoteContentService.toRawUrl(text)
        if (sourceLocation.startsWith("http://") || sourceLocation.startsWith("https://")) {
            // GitLab API URL: https://{host}/api/v4/projects/{id}/repository/files/{encodedCatalogPath}/raw?ref={ref}
            val gitlabApiRawRegex = Regex("""^(https?://[^/]+/api/v4/projects/[^/]+/repository/files/)(.+)(/raw\?ref=.+)$""")
            val match = gitlabApiRawRegex.matchEntire(sourceLocation)
            if (match != null) {
                val apiBase    = match.groupValues[1]   // "https://host/api/v4/projects/{id}/repository/files/"
                val refSuffix  = match.groupValues[3]   // "/raw?ref=develop"
                // Decode the encoded catalog file path to get its directory
                val encodedCatalogPath = match.groupValues[2]  // e.g. "some%2Fpath%2Fcatalog-info.yaml"
                val catalogFilePath = java.net.URLDecoder.decode(encodedCatalogPath, java.nio.charset.StandardCharsets.UTF_8)
                val catalogDir = if (catalogFilePath.contains("/")) catalogFilePath.substringBeforeLast("/") else ""
                val relPath = text.removePrefix("./")
                val resolvedPath = if (catalogDir.isEmpty()) relPath else "$catalogDir/$relPath"
                val encodedPath = java.net.URLEncoder.encode(resolvedPath, java.nio.charset.StandardCharsets.UTF_8)
                return "$apiBase$encodedPath$refSuffix"
            }
            // GitHub / other remote: resolve relative to the directory of the source URL
            val baseDir = sourceLocation.substringBeforeLast("/")
            return "$baseDir/${text.removePrefix("./")}"
        }
        return text
    }

    private fun resolveLocalFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        val cwd = File(System.getProperty("user.dir"))
        val fromCwd = File(cwd, path)
        if (fromCwd.exists()) return fromCwd
        val fromParent = File(cwd.parentFile ?: cwd, path)
        if (fromParent.exists()) return fromParent
        return fromCwd
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTargets(doc: Map<*, *>): List<String> {
        val spec = doc["spec"] as? Map<*, *> ?: return emptyList()
        val targets = spec["targets"] as? List<*> ?: return emptyList()
        return targets.filterIsInstance<String>()
    }

    private fun parseFirstDocument(content: String): Map<*, *> {
        return yamlMapper.readValue(content, Map::class.java) ?: emptyMap<Any, Any>()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAllDocuments(content: String): List<Map<*, *>> {
        val docs = mutableListOf<Map<*, *>>()
        val parts = content.split(Regex("(?m)^---\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            try {
                val doc = yamlMapper.readValue(part, Map::class.java)
                if (doc != null) docs.add(doc)
            } catch (e: Exception) {
                log.warn("Failed to parse YAML document: ${e.message}")
            }
        }
        return docs
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToEo(doc: Map<*, *>, sourceLocation: String = ""): CatalogEo? {
        val kind = doc["kind"] as? String ?: return null
        val metaMap = doc["metadata"] as? Map<*, *> ?: return null
        val specMap = doc["spec"] as? Map<*, *> ?: emptyMap<Any, Any>()

        val name = metaMap["name"] as? String ?: return null
        val description = metaMap["description"] as? String
        val tags = (metaMap["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val annotations = (metaMap["annotations"] as? Map<*, *>)
            ?.entries?.associate { (k, v) -> k.toString() to v.toString() } ?: emptyMap()
        val links = (metaMap["links"] as? List<*>)?.mapNotNull { link ->
            val lm = link as? Map<*, *> ?: return@mapNotNull null
            LinkEo(url = lm["url"] as? String ?: return@mapNotNull null, title = lm["title"] as? String)
        } ?: emptyList()

        val type = specMap["type"] as? String
        val lifecycle = specMap["lifecycle"] as? String
        val owner = specMap["owner"] as? String
        val providesApis  = (specMap["providesApis"]  as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val dependsOn     = (specMap["dependsOn"]     as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val dependencyOf  = (specMap["dependencyOf"]  as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val defMap = specMap["definition"] as? Map<*, *>
        val definition = defMap?.let {
            DefinitionEo(text = (it["\$text"] as? String)?.let { text -> resolveDefinitionUrl(text, sourceLocation) })
        }

        return CatalogEo(
            kind = kind,
            metadata = MetadataEo(name, description, tags, annotations, links),
            spec = SpecEo(type, lifecycle, owner, providesApis, dependsOn, dependencyOf, definition)
        )
    }
}
