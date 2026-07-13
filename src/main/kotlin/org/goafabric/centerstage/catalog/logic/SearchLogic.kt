package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.controller.dto.SearchResult
import org.goafabric.centerstage.catalog.persistence.AdrRepository
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.DocRepository

@ApplicationScoped
class SearchLogic(
    val componentRepo: ComponentRepository,
    val adrRepo: AdrRepository,
    val docRepo: DocRepository
) {

    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return searchComponents(query) + searchAdrs(query) + searchDocs(query)
    }

    private fun searchComponents(query: String): List<SearchResult> =
        componentRepo.search("%$query%").map { eo ->
            SearchResult(
                type          = eo.kind.lowercase(),
                componentName = eo.name,
                name          = eo.name,
                excerpt       = excerpt(eo.searchText, query)
            )
        }

    private fun searchAdrs(query: String): List<SearchResult> =
        adrRepo.search("%$query%").map { eo ->
            SearchResult(
                type          = "adr",
                componentName = eo.componentName,
                name          = eo.name,
                excerpt       = excerpt(eo.searchText, query)
            )
        }

    private fun searchDocs(query: String): List<SearchResult> =
        docRepo.search("%$query%").map { eo ->
            SearchResult(
                type          = "doc",
                componentName = eo.componentName,
                name          = eo.name,
                excerpt       = excerpt(eo.searchText, query)
            )
        }

    private fun excerpt(text: String?, query: String): String {
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
