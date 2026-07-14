package org.goafabric.centerstage.catalog.logic.mapper

import org.goafabric.centerstage.catalog.controller.dto.GraphNode
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.mapstruct.Context
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Named
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface GraphMapper {

    @Mapping(target = "id",      source = "name")
    @Mapping(target = "label",   source = "name")
    @Mapping(target = "type",    expression = "java(kindToType(eo.getKind()))")
    @Mapping(target = "kind",    source = "kind")
    @Mapping(target = "isFocus", expression = "java(isFocus)")
    fun toGraphNode(eo: ComponentEo, @Context isFocus: Boolean): GraphNode

    @Named("kindToType")
    fun kindToType(kind: String?): String = when (kind) {
        "Component" -> "component"
        "Resource"  -> "resource"
        "API"       -> "api"
        else        -> "other"
    }
}
