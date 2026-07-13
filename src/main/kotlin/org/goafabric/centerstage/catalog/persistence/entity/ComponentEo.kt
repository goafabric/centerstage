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
@Table(name = "component_eo")
class ComponentEo : PanacheEntity.Managed {
    @Id
    var id: String = ""

    var name: String = ""
    var kind: String = ""
    var type: String? = null
    var lifecycle: String? = null
    var owner: String? = null

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    /** Comma-separated tags */
    @Column(columnDefinition = "TEXT")
    var tags: String? = null

    /** Pipe-delimited entries: title|url,title|url */
    @Column(columnDefinition = "TEXT")
    var links: String? = null

    /** Comma-separated key=value pairs */
    @Column(columnDefinition = "TEXT")
    var annotations: String? = null

    @Column(name = "provides_apis", columnDefinition = "TEXT")
    var providesApis: String? = null

    @Column(name = "depends_on", columnDefinition = "TEXT")
    var dependsOn: String? = null

    @Column(name = "dependency_of", columnDefinition = "TEXT")
    var dependencyOf: String? = null

    @Column(name = "source_path")
    var sourcePath: String? = null

    /** Resolved raw URL for spec.definition.$text (API entries only) */
    @Column(name = "definition_url", columnDefinition = "TEXT")
    var definitionUrl: String? = null

    /** Concatenation of all searchable text fields for LIKE queries */
    @Column(name = "search_text", columnDefinition = "TEXT")
    var searchText: String? = null

    interface Repo : PanacheRepository.Managed<ComponentEo, String> {

        @Find
        fun findByKind(kind: String): List<ComponentEo>

        @Query("from ComponentEo where kind = :kind and name = :name")
        fun findByKindAndName(kind: String, name: String): List<ComponentEo>

        @Query("from ComponentEo where searchText like :query")
        fun search(query: String): List<ComponentEo>

        fun save(eo: ComponentEo): ComponentEo = session.merge(eo)
    }
}
