package org.goafabric.centerstage.catalog.logic.mapper

import org.goafabric.centerstage.catalog.controller.dto.Api
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.controller.dto.Link
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface ComponentMapper {

    @Mapping(source = "tags",         target = "tags",         qualifiedByName = ["splitList"])
    @Mapping(source = "annotations",  target = "annotations",  qualifiedByName = ["splitMap"])
    @Mapping(source = "links",        target = "links",        qualifiedByName = ["splitLinks"])
    @Mapping(source = "providesApis", target = "providesApis", qualifiedByName = ["splitList"])
    fun toComponent(eo: ComponentEo): Component

    @Mapping(source = "definitionUrl", target = "definitionUrl")
    fun toApi(eo: ComponentEo): Api

    @Named("splitList")
    fun splitList(value: String?): List<String> =
        value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    @Named("splitMap")
    fun splitMap(value: String?): Map<String, String> =
        value?.split(",")
            ?.filter { it.contains("=") }
            ?.associate { it.substringBefore("=") to it.substringAfter("=") }
            ?: emptyMap()

    @Named("splitLinks")
    fun splitLinks(value: String?): List<Link> =
        value?.split(",")
            ?.filter { it.contains("|") }
            ?.map { Link(url = it.substringAfter("|"), title = it.substringBefore("|").ifEmpty { null }) }
            ?: emptyList()
}
