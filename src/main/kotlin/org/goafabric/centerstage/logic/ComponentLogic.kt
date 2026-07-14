package org.goafabric.centerstage.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.controller.dto.Component
import org.goafabric.centerstage.logic.mapper.ComponentMapper
import org.goafabric.centerstage.persistence.ComponentRepository

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
