package org.goafabric.centerstage.catalog.persistence

import io.quarkus.hibernate.panache.PanacheRepository
import jakarta.data.repository.Find
import jakarta.data.repository.Query
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo

interface ComponentRepository : PanacheRepository.Managed<ComponentEo, String> {

    @Find
    fun findByKind(kind: String): List<ComponentEo>

    @Query("from ComponentEo where kind = :kind and name = :name")
    fun findByKindAndName(kind: String, name: String): List<ComponentEo>

    @Query("from ComponentEo where searchText like :query")
    fun search(query: String): List<ComponentEo>

    fun save(eo: ComponentEo): ComponentEo = session.merge(eo)
}
