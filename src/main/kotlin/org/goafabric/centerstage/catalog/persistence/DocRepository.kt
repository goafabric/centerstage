package org.goafabric.centerstage.catalog.persistence

import io.quarkus.hibernate.panache.PanacheRepository
import jakarta.data.repository.Find
import jakarta.data.repository.Query
import org.goafabric.centerstage.catalog.persistence.entity.DocEo

interface DocRepository : PanacheRepository.Managed<DocEo, String> {

    @Find
    fun findByComponentName(componentName: String): List<DocEo>

    @Query("from DocEo where searchText like :query")
    fun search(query: String): List<DocEo>

    fun save(eo: DocEo): DocEo = session.merge(eo)
}
