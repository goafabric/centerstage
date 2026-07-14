package org.goafabric.centerstage.logic

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.goafabric.centerstage.adapter.RemoteContentService
import org.goafabric.centerstage.persistence.entity.ComponentEo
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

@ApplicationScoped
class CatalogLoaderLogic(val remoteContentService: RemoteContentService) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @ConfigProperty(name = "centerstage.catalog.file", defaultValue = "doc/catalog/entities-local.yaml")
    lateinit var catalogFile: String

    val entries: MutableList<ComponentEo> = mutableListOf()

    val yamlMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun load() {
        entries.clear()
        log.info("Loading catalog from: $catalogFile")
        val entryContent = readContent(catalogFile) ?: return logWarn("Catalog file not found: $catalogFile")
        extractTargets(parseFirstDocument(entryContent))
            .forEach { loadTarget(it) }
        log.info("Catalog loaded: ${entries.size} entries (${entries.count { it.kind == "Component" }} components)")
    }

    private fun loadTarget(target: String) {
        val normalizedTarget = normalizeTarget(target)
        val content = readContent(normalizedTarget) ?: return logWarn("Target not found: $normalizedTarget")
        parseAllDocuments(content)
            .mapNotNull { parseEntry(it, normalizedTarget) }
            .forEach { entries.add(it).also { _ -> log.debug("Loaded ${it.kind}: ${it.name}") } }
    }

    private fun normalizeTarget(target: String): String {
        val resolved = resolveTarget(catalogFile, target)
        return if (resolved.startsWith("http://") || resolved.startsWith("https://"))
            remoteContentService.toRawUrl(resolved) else resolved
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseEntry(doc: Map<*, *>, sourcePath: String): ComponentEo? {
        val kind = doc["kind"] as? String ?: return null
        val meta = doc["metadata"] as? Map<*, *> ?: return null
        val spec = doc["spec"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val name = meta["name"] as? String ?: return null

        return ComponentEo().apply {
            this.id            = UUID.randomUUID().toString()
            this.kind          = kind
            this.name          = name
            this.type          = spec["type"] as? String
            this.lifecycle     = spec["lifecycle"] as? String
            this.owner         = spec["owner"] as? String
            this.description   = meta["description"] as? String
            this.tags          = parseTags(meta)
            this.annotations   = parseAnnotations(meta)
            this.links         = parseLinks(meta)
            this.providesApis  = parseList(spec, "providesApis")
            this.dependsOn     = parseList(spec, "dependsOn")
            this.dependencyOf  = parseList(spec, "dependencyOf")
            this.sourcePath    = sourcePath
            this.definitionUrl = parseDefinitionUrl(spec, sourcePath)
            this.searchText    = buildSearchText(name, meta, spec, this.tags)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTags(meta: Map<*, *>): String? =
        (meta["tags"] as? List<*>)?.filterIsInstance<String>()?.joinToString(",")

    @Suppress("UNCHECKED_CAST")
    private fun parseAnnotations(meta: Map<*, *>): String? =
        (meta["annotations"] as? Map<*, *>)?.entries?.joinToString(",") { "${it.key}=${it.value}" }

    @Suppress("UNCHECKED_CAST")
    private fun parseLinks(meta: Map<*, *>): String? =
        (meta["links"] as? List<*>)?.mapNotNull { lm ->
            val m = lm as? Map<*, *> ?: return@mapNotNull null
            val url = m["url"] as? String ?: return@mapNotNull null
            "${m["title"] ?: ""}|$url"
        }?.joinToString(",")

    @Suppress("UNCHECKED_CAST")
    private fun parseList(spec: Map<*, *>, key: String): String? =
        (spec[key] as? List<*>)?.filterIsInstance<String>()?.joinToString(",")

    @Suppress("UNCHECKED_CAST")
    private fun parseDefinitionUrl(spec: Map<*, *>, sourcePath: String): String? =
        (spec["definition"] as? Map<*, *>)
            ?.let { (it["\$text"] as? String)?.let { text -> resolveDefinitionUrl(text, sourcePath) } }

    @Suppress("UNCHECKED_CAST")
    private fun buildSearchText(name: String, meta: Map<*, *>, spec: Map<*, *>, tags: String?): String =
        listOfNotNull(
            name, meta["description"] as? String, spec["owner"] as? String,
            spec["type"] as? String, spec["lifecycle"] as? String, tags,
            (meta["annotations"] as? Map<*, *>)?.values?.joinToString(" ")
        ).joinToString(" ")

    private fun resolveDefinitionUrl(text: String, sourceLocation: String): String {
        if (text.startsWith("http://") || text.startsWith("https://")) return remoteContentService.toRawUrl(text)
        if (sourceLocation.startsWith("http://") || sourceLocation.startsWith("https://")) {
            return resolveRemoteDefinitionUrl(text, sourceLocation)
        }
        return text
    }

    private fun resolveRemoteDefinitionUrl(text: String, sourceLocation: String): String {
        val gitlabMatch = Regex("""^(https?://[^/]+/api/v4/projects/[^/]+/repository/files/)(.+)(/raw\?ref=.+)$""")
            .matchEntire(sourceLocation)
        if (gitlabMatch != null) {
            val apiBase       = gitlabMatch.groupValues[1]
            val refSuffix     = gitlabMatch.groupValues[3]
            val catalogPath   = URLDecoder.decode(gitlabMatch.groupValues[2], StandardCharsets.UTF_8)
            val catalogDir    = if (catalogPath.contains("/")) catalogPath.substringBeforeLast("/") else ""
            val relPath       = text.removePrefix("./")
            val resolvedPath  = if (catalogDir.isEmpty()) relPath else "$catalogDir/$relPath"
            return "$apiBase${URLEncoder.encode(resolvedPath, StandardCharsets.UTF_8)}$refSuffix"
        }
        return "${sourceLocation.substringBeforeLast("/")}/${text.removePrefix("./")}"
    }

    private fun readContent(path: String): String? =
        if (path.startsWith("http://") || path.startsWith("https://"))
            remoteContentService.fetchTextOrNull(remoteContentService.toRawUrl(path))
        else resolveLocalFile(path).takeIf { it.exists() }?.readText()

    private fun resolveTarget(base: String, target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        return if (base.startsWith("http://") || base.startsWith("https://"))
            "${base.substringBeforeLast("/")}/${target.removePrefix("./")}"
        else File(resolveLocalFile(base).parentFile, target.removePrefix("./")).path
    }

    private fun resolveLocalFile(path: String): File {
        val f = File(path)
        if (f.isAbsolute) return f
        val cwd = File(System.getProperty("user.dir"))
        return File(cwd, path).takeIf { it.exists() }
            ?: File(cwd.parentFile ?: cwd, path).takeIf { it.exists() }
            ?: File(cwd, path)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractTargets(doc: Map<*, *>): List<String> =
        ((doc["spec"] as? Map<*, *>)?.get("targets") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    private fun parseFirstDocument(content: String): Map<*, *> =
        yamlMapper.readValue(content, Map::class.java) ?: emptyMap<Any, Any>()

    private fun parseAllDocuments(content: String): List<Map<*, *>> =
        content.split(Regex("(?m)^---\\s*$")).map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { runCatching { yamlMapper.readValue(it, Map::class.java) }.getOrNull() }

    private fun logWarn(msg: String): Unit = log.warn(msg)
}
