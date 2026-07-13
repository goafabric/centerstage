package org.goafabric.centerstage.catalog.persistence

import io.quarkus.hibernate.panache.PanacheRepository
import jakarta.data.repository.Find
import jakarta.data.repository.Query
import org.goafabric.centerstage.catalog.persistence.entity.AdrEo

interface AdrRepository : PanacheRepository.Managed<AdrEo, String> {

    @Find
    fun findByComponentName(componentName: String): List<AdrEo>

    @Query("from AdrEo where searchText like :query")
    fun search(query: String): List<AdrEo>

    fun save(eo: AdrEo): AdrEo = session.merge(eo)
}
