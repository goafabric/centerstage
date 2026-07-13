package org.goafabric.centerstage.catalog.persistence.mapper

import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.CatalogEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DefinitionEo
import org.goafabric.centerstage.catalog.persistence.entity.LinkEo
import org.goafabric.centerstage.catalog.persistence.entity.MetadataEo
import org.goafabric.centerstage.catalog.persistence.entity.SpecEo
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface PersistenceMapper {

    // ---- CatalogEo → ComponentEo ----------------------------------------

    @Mapping(source = "metadata.name",        target = "name")
    @Mapping(source = "metadata.description", target = "description")
    @Mapping(source = "metadata.tags",        target = "tags",        qualifiedByName = ["listToString"])
    @Mapping(source = "metadata.links",       target = "links",       qualifiedByName = ["linksToString"])
    @Mapping(source = "metadata.annotations", target = "annotations", qualifiedByName = ["mapToString"])
    @Mapping(source = "spec.type",            target = "type")
    @Mapping(source = "spec.lifecycle",       target = "lifecycle")
    @Mapping(source = "spec.owner",           target = "owner")
    @Mapping(source = "spec.providesApis",    target = "providesApis", qualifiedByName = ["listToString"])
    @Mapping(source = "spec.dependsOn",       target = "dependsOn",    qualifiedByName = ["listToString"])
    @Mapping(source = "spec.dependencyOf",    target = "dependencyOf", qualifiedByName = ["listToString"])
    @Mapping(source = "spec.definition.text", target = "definitionUrl")
    @Mapping(source = "sourcePath",           target = "sourcePath")
    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "searchText", ignore = true)
    fun toComponentEo(eo: CatalogEo): ComponentEo

    // ---- ComponentEo → CatalogEo ----------------------------------------

    @Mapping(target = "kind",     source = "kind")
    @Mapping(target = "sourcePath", source = "sourcePath")
    @Mapping(target = "metadata", expression = "java(toMetadataEo(eo))")
    @Mapping(target = "spec",     expression = "java(toSpecEo(eo))")
    fun toCatalogEo(eo: ComponentEo): CatalogEo

    fun toMetadataEo(eo: ComponentEo): MetadataEo = MetadataEo(
        name        = eo.name,
        description = eo.description,
        tags        = splitList(eo.tags),
        annotations = splitMap(eo.annotations),
        links       = splitLinks(eo.links)
    )

    fun toSpecEo(eo: ComponentEo): SpecEo = SpecEo(
        type         = eo.type,
        lifecycle    = eo.lifecycle,
        owner        = eo.owner,
        providesApis = splitList(eo.providesApis),
        dependsOn    = splitList(eo.dependsOn),
        dependencyOf = splitList(eo.dependencyOf),
        definition   = eo.definitionUrl?.let { DefinitionEo(text = it) }
    )

    // ---- Named converters -----------------------------------------------

    @Named("listToString")
    fun listToString(list: List<String>?): String? =
        list?.filter { it.isNotEmpty() }?.joinToString(",")?.ifEmpty { null }

    @Named("linksToString")
    fun linksToString(links: List<LinkEo>?): String? =
        links?.joinToString(",") { "${it.title ?: ""}|${it.url}" }?.ifEmpty { null }

    @Named("mapToString")
    fun mapToString(map: Map<String, String>?): String? =
        map?.entries?.joinToString(",") { "${it.key}=${it.value}" }?.ifEmpty { null }

    // ---- Private split helpers ------------------------------------------

    fun splitList(value: String?): List<String> =
        value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    fun splitMap(value: String?): Map<String, String> =
        value?.split(",")
            ?.filter { it.contains("=") }
            ?.associate { it.substringBefore("=") to it.substringAfter("=") }
            ?: emptyMap()

    fun splitLinks(value: String?): List<LinkEo> =
        value?.split(",")
            ?.filter { it.contains("|") }
            ?.map { LinkEo(url = it.substringAfter("|"), title = it.substringBefore("|").ifEmpty { null }) }
            ?: emptyList()
}
