package org.goafabric.centerstage.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.adapter.RemoteContentService
import org.goafabric.centerstage.controller.dto.Api
import org.goafabric.centerstage.logic.mapper.ComponentMapper
import org.goafabric.centerstage.persistence.ComponentRepository

@ApplicationScoped
class ApiLogic(
    val componentMapper: ComponentMapper,
    val remoteContentService: RemoteContentService
) {
    @Inject lateinit var componentRepo: ComponentRepository

    fun getAllApis(): List<Api> =
        componentRepo.findByKind("API").map { componentMapper.toApi(it) }

    fun getApis(componentName: String): List<Api> {
        val component = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?: throw NoSuchElementException("Component not found: $componentName")
        val apiNames = component.splitList(component.providesApis)
        return componentRepo.findByKind("API")
            .filter { it.name in apiNames }
            .map { componentMapper.toApi(it) }
    }

    fun getApiSpec(componentName: String): String {
        val definitionUrl = getApis(componentName)
            .firstOrNull { it.type == "openapi" && it.definitionUrl != null }?.definitionUrl
            ?: throw NoSuchElementException("No OpenAPI definition found for component: $componentName")
        return remoteContentService.fetchText(remoteContentService.toRawUrl(definitionUrl))
    }

    fun getApiSpecByName(apiName: String): String {
        val definitionUrl = componentRepo.findByKindAndName("API", apiName).firstOrNull()?.definitionUrl
            ?: throw NoSuchElementException("No definition URL for API: $apiName")
        return remoteContentService.fetchText(remoteContentService.toRawUrl(definitionUrl))
    }
}
