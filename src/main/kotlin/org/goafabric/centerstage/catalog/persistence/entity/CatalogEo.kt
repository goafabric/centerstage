package org.goafabric.centerstage.catalog.persistence.entity

data class CatalogEo(
    val kind: String,
    val metadata: MetadataEo,
    val spec: SpecEo,
    val sourcePath: String? = null   // absolute path of the catalog-info.yaml this entry was loaded from
)

data class MetadataEo(
    val name: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val annotations: Map<String, String> = emptyMap(),
    val links: List<LinkEo> = emptyList()
)

data class LinkEo(
    val url: String,
    val title: String? = null
)

data class SpecEo(
    val type: String? = null,
    val lifecycle: String? = null,
    val owner: String? = null,
    val providesApis: List<String> = emptyList(),
    val dependsOn: List<String> = emptyList(),
    val dependencyOf: List<String> = emptyList(),
    val definition: DefinitionEo? = null
)

data class DefinitionEo(
    val text: String? = null   // maps from $text in YAML
)
