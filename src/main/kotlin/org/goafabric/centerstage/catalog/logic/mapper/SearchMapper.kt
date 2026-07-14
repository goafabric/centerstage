package org.goafabric.centerstage.catalog.logic.mapper

import org.goafabric.centerstage.catalog.controller.dto.SearchResult
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.entity.DocEo
import org.mapstruct.Context
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.ReportingPolicy

@Mapper(componentModel = "cdi", unmappedTargetPolicy = ReportingPolicy.IGNORE)
interface SearchMapper {

    @Mapping(target = "type",          expression = "java(eo.getKind().toLowerCase())")
    @Mapping(target = "componentName", expression = "java(eo.getName())")
    @Mapping(target = "name",          expression = "java(eo.getName())")
    @Mapping(target = "excerpt",       expression = "java(excerpt(eo.getSearchText(), query))")
    fun toSearchResult(eo: ComponentEo, @Context query: String): SearchResult

    @Mapping(target = "type",    constant = "adr")
    @Mapping(target = "excerpt", expression = "java(excerpt(eo.getSearchText(), query))")
    fun toSearchResult(eo: AdrEo, @Context query: String): SearchResult

    @Mapping(target = "type",    constant = "doc")
    @Mapping(target = "excerpt", expression = "java(excerpt(eo.getSearchText(), query))")
    fun toSearchResult(eo: DocEo, @Context query: String): SearchResult

    fun excerpt(text: String?, query: String): String {
        if (text == null) return ""
        val lower = text.lowercase()
        val idx   = lower.indexOf(query.lowercase())
        if (idx < 0) return text.take(80)
        val start = maxOf(0, idx - 30)
        val end   = minOf(text.length, idx + query.length + 50)
        val snip  = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        return if (start > 0) "…$snip" else snip
    }
}
