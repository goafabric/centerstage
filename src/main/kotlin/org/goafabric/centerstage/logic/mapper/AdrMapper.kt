package org.goafabric.centerstage.logic.mapper

import org.goafabric.centerstage.controller.dto.Adr
import org.goafabric.centerstage.persistence.entity.AdrEo
import org.mapstruct.Mapper
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface AdrMapper {

    fun toAdr(eo: AdrEo): Adr
}
