package org.goafabric.centerstage.catalog.logic.mapper

import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.controller.dto.Api
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.controller.dto.Link
import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.AdrFileEo
import org.goafabric.centerstage.catalog.persistence.entity.CatalogEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface CatalogMapper {

    @Mapping(source = "metadata.name", target = "name")
    @Mapping(source = "metadata.description", target = "description")
    @Mapping(source = "metadata.tags", target = "tags")
    @Mapping(source = "metadata.annotations", target = "annotations")
    @Mapping(source = "metadata.links", target = "links")
    @Mapping(source = "spec.owner", target = "owner")
    @Mapping(source = "spec.type", target = "type")
    @Mapping(source = "spec.lifecycle", target = "lifecycle")
    @Mapping(source = "spec.providesApis", target = "providesApis")
    fun toComponent(eo: CatalogEo): Component

    @Mapping(source = "metadata.name", target = "name")
    @Mapping(source = "metadata.description", target = "description")
    @Mapping(source = "spec.type", target = "type")
    @Mapping(source = "spec.lifecycle", target = "lifecycle")
    @Mapping(source = "spec.definition.text", target = "definitionUrl")
    fun toApi(eo: CatalogEo): Api

    fun toLink(eo: org.goafabric.centerstage.catalog.persistence.entity.LinkEo): Link

    fun toAdr(eo: AdrFileEo): Adr

    fun toAdr(eo: AdrEo): Adr

    fun toTechDoc(eo: DocEo): TechDoc
}
