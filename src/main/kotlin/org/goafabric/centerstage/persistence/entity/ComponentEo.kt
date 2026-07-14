package org.goafabric.centerstage.persistence.entity

import io.quarkus.hibernate.panache.PanacheEntity
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

    /** Returns the value of a named annotation from the comma-separated key=value string. */
    fun annotation(key: String): String? =
        annotations?.split(",")?.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")

    /** Splits a comma-separated field into a list, filtering blanks. */
    fun splitList(value: String?): List<String> =
        value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
}
