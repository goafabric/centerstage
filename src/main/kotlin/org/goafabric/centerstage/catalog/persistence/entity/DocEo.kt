package org.goafabric.centerstage.catalog.persistence.entity

import io.quarkus.hibernate.panache.PanacheEntity
import io.quarkus.hibernate.panache.PanacheRepository
import jakarta.data.repository.Find
import jakarta.data.repository.Query
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "doc_eo")
class DocEo : PanacheEntity.Managed {
    @Id
    var id: String = ""

    @Column(name = "component_name")
    var componentName: String = ""

    var name: String = ""

    @Column(columnDefinition = "TEXT")
    var content: String? = null

    @Column(name = "search_text", columnDefinition = "TEXT")
    var searchText: String? = null

    interface Repo : PanacheRepository.Managed<DocEo, String> {

        @Find
        fun findByComponentName(componentName: String): List<DocEo>

        @Query("from DocEo where searchText like :query")
        fun search(query: String): List<DocEo>

        fun save(eo: DocEo): DocEo = session.merge(eo)
    }
}
