package org.goafabric.centerstage.catalog.logic

import jakarta.enterprise.context.ApplicationScoped
import org.goafabric.centerstage.catalog.adapter.RemoteContentService
import org.goafabric.centerstage.catalog.controller.dto.Api
import org.goafabric.centerstage.catalog.logic.mapper.CatalogMapper

@ApplicationScoped
class ApiLogic(
    val catalogLoaderLogic: CatalogLoaderLogic,
    val catalogMapper: CatalogMapper,
    val remoteContentService: RemoteContentService
) {

    fun getAllApis(): List<Api> =
        catalogLoaderLogic.entries
            .filter { it.kind == "API" }
            .map { catalogMapper.toApi(it) }

    fun getApis(componentName: String): List<Api> {
        val component = catalogLoaderLogic.entries
            .filter { it.kind == "Component" }
            .firstOrNull { it.metadata.name == componentName }
            ?: throw NoSuchElementException("Component not found: $componentName")

        val apiNames = component.spec.providesApis
        return catalogLoaderLogic.entries
            .filter { it.kind == "API" && it.metadata.name in apiNames }
            .map { catalogMapper.toApi(it) }
    }

    fun getApiSpec(componentName: String): String {
        val apis = getApis(componentName)
        val definitionUrl = apis.firstOrNull { it.type == "openapi" && it.definitionUrl != null }?.definitionUrl
            ?: throw NoSuchElementException("No OpenAPI definition found for component: $componentName")
        return remoteContentService.fetchText(remoteContentService.toRawUrl(definitionUrl))
    }

    fun getApiSpecByName(apiName: String): String {
        val api = catalogLoaderLogic.entries
            .filter { it.kind == "API" }
            .firstOrNull { it.metadata.name == apiName }
            ?: throw NoSuchElementException("API not found: $apiName")
        val definitionUrl = api.spec.definition?.text
            ?: throw NoSuchElementException("No definition URL for API: $apiName")
        return remoteContentService.fetchText(remoteContentService.toRawUrl(definitionUrl))
    }
}
