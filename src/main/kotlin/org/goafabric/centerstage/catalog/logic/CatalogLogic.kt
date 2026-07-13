package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper

@ApplicationScoped
class CatalogLogic(
    val catalogLoaderLogic: CatalogLoaderLogic,
    val catalogMapper: CatalogMapper
) {

    fun getComponents(): List<Component> =
        catalogLoaderLogic.entries
            .filter { it.kind == "Component" }
            .map { catalogMapper.toComponent(it) }

    fun getComponent(name: String): Component =
        catalogLoaderLogic.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == name }
            ?.let { catalogMapper.toComponent(it) }
            ?: throw NoSuchElementException("Component not found: $name")
}
