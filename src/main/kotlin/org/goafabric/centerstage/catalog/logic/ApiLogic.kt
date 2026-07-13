package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.Api
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper
import org.goafabric.centerstage.catalog.persistence.entity.ComponentEo
import org.goafabric.centerstage.catalog.persistence.mapper.PersistenceMapper

@ApplicationScoped
class ApiLogic(
    val persistenceMapper: PersistenceMapper,
    val catalogMapper: CatalogMapper,
    val remoteContentService: RemoteContentService
) {
    @Inject lateinit var componentRepo: ComponentEo.Repo

    fun getAllApis(): List<Api> =
        componentRepo.findByKind("API")
            .map { catalogMapper.toApi(persistenceMapper.toCatalogEo(it)) }

    fun getApis(componentName: String): List<Api> {
        val component = componentRepo.findByKindAndName("Component", componentName).firstOrNull()
            ?.let { persistenceMapper.toCatalogEo(it) }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val apiNames = component.spec.providesApis
        return componentRepo.findByKind("API")
            .filter { it.name in apiNames }
            .map { catalogMapper.toApi(persistenceMapper.toCatalogEo(it)) }
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
