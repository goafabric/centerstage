package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.controller.dto.Component
import org.goafabric.centerstage.catalog.logic.mapper.ComponentMapper
import org.goafabric.centerstage.catalog.persistence.ComponentRepository
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo

@ApplicationScoped
class ComponentLogic(val componentMapper: ComponentMapper) {
    @Inject lateinit var componentRepo: ComponentRepository

    fun getComponents(): List<Component> =
        componentRepo.findByKind("Component").map { componentMapper.toComponent(it) }

    fun getComponent(name: String): Component =
        componentRepo.findByKindAndName("Component", name).firstOrNull()
            ?.let { componentMapper.toComponent(it) }
            ?: throw NoSuchElementException("Component not found: $name")
}
