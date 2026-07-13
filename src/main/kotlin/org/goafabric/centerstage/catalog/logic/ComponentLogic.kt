package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper

@ApplicationScoped
class ComponentLogic(
    val persistenceMapper: PersistenceMapper,
    val catalogMapper: CatalogMapper
) {
    @Inject lateinit var componentRepo: ComponentEo.Repo

    fun getComponents(): List<Component> =
        componentRepo.findByKind("Component")
            .map { catalogMapper.toComponent(persistenceMapper.toCatalogEo(it)) }

    fun getComponent(name: String): Component =
        componentRepo.findByKindAndName("Component", name).firstOrNull()
            ?.let { catalogMapper.toComponent(persistenceMapper.toCatalogEo(it)) }
            ?: throw NoSuchElementException("Component not found: $name")
}
