package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.controller.dto.SearchResult
import org.goafabric.centerstage.catalog.logic.mapper.SearchMapper
import org.goafabric.centerstage.catalog.persistence.AdrRepository
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.DocRepository

@ApplicationScoped
class SearchLogic(
    val componentRepo: ComponentRepository,
    val adrRepo: AdrRepository,
    val docRepo: DocRepository,
    val searchMapper: SearchMapper
) {

    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        return searchComponents(query) + searchAdrs(query) + searchDocs(query)
    }

    private fun searchComponents(query: String): List<SearchResult> =
        componentRepo.search("%$query%").map { searchMapper.toSearchResult(it, query) }

    private fun searchAdrs(query: String): List<SearchResult> =
        adrRepo.search("%$query%").map { searchMapper.toSearchResult(it, query) }

    private fun searchDocs(query: String): List<SearchResult> =
        docRepo.search("%$query%").map { searchMapper.toSearchResult(it, query) }
}
