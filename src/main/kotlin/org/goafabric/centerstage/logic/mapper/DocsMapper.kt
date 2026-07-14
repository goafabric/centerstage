package org.goafabric.centerstage.logic.mapper

import org.goafabric.centerstage.controller.dto.TechDoc
import org.goafabric.centerstage.persistence.entity.DocEo
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface DocsMapper {

    fun toTechDoc(eo: DocEo): TechDoc
}
