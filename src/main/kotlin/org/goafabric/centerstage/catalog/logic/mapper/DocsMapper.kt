package org.goafabric.centerstage.catalog.logic.mapper

import org.goafabric.centerstage.catalog.controller.dto.TechDoc
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface DocsMapper {

    fun toTechDoc(eo: DocEo): TechDoc
}
