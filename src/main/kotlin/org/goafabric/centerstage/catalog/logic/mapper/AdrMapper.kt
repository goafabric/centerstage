package org.goafabric.centerstage.catalog.logic.mapper

import org.goafabric.centerstage.catalog.controller.dto.Adr
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface AdrMapper {

    fun toAdr(eo: AdrEo): Adr
}
