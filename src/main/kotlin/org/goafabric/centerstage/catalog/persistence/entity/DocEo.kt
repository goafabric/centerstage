package org.goafabric.centerstage.catalog.persistence.entity

import io.quarkus.hibernate.panache.PanacheEntity
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
}
