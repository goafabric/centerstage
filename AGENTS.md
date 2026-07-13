# centerstage

A Backstage-inspired developer portal built with Quarkus 3.37.0 + Kotlin + plain HTML/JS/CSS.
It parses Backstage `catalog-info.yaml` files, persists the catalog to H2/PostgreSQL, and renders
a web UI for browsing components, APIs, ADRs, an interactive dependency graph, TechDocs, and a tech radar.

---

## Build

- **Runtime**: Java 25, Quarkus 3.37.0, Kotlin 2.4.0
- **Build tool**: Gradle 9.5.1 (`./gradlew`)
- **Key commands**:
  - `./gradlew test` — run all tests (integration + unit + archunit)
  - `./gradlew build -x test` — build the jar
  - `java -jar build/quarkus-app/quarkus-run.jar` — run locally on port 50700
  - `./gradlew dockerImageNative` — build + push native Docker image

---

## Configuration (`application.properties`)

| Property | Default | Description |
|---|---|---|
| `centerstage.catalog.file` | `doc/catalog/entities-local.yaml` | Entry-point catalog file (local path or GitLab/GitHub blob URL) |
| `centerstage.ingestion.interval` | `PT30M` | ISO-8601 duration between scheduled re-ingestion runs |
| `techradar.url` | *(GitHub JSON URL)* | URL to tech radar JSON (GitHub blob URL supported) |
| `gitlab.token` | *(empty)* | GitLab API token; falls back to `GITLAB_TOKEN` env var |
| `quarkus.rest-client.GitHubAdapter.url` | `https://api.github.com` | GitHub REST client base URL |
| `quarkus.rest-client.GitLabAdapter.url` | *(self-hosted host)* | GitLab REST client base URL |
| `quarkus.datasource.db-kind` | `h2` | Database kind (`h2` dev/test, `postgresql` prod) |
| `quarkus.http.port` | `50700` | HTTP port |

---

## Architecture

Strictly layered by package. Each layer only accesses the layer below it.

```
controller        → REST endpoints, pure delegation to logic, builds Response
controller/dto    → DTOs: simple names (Component, Api, Adr, TechDoc, Graph) — no Dto/Response suffix
logic             → Business logic, constructor-injected; newspaper-method style (short public methods, named private helpers)
logic/mapper      → MapStruct mappers (CatalogMapper): entity → DTO
adapter           → Outbound HTTP (REST clients + service classes)
persistence       → PanacheRepository.Managed interfaces (standalone files in persistence/)
persistence/entity → JPA entities (@Entity, PanacheEntity.Managed) — one entity = one table
```

**Key rules from `doc/inst.md`:**
- Repositories are based on `PanacheRepository.Managed` (not Jakarta Data `CrudRepository` — stateless session has limitations)
- `jakarta.data` annotations (`@Find`, `@Query`) are allowed on repository methods
- Prefer constructor injection; `@Inject lateinit var` only where unavoidable
- Logic methods follow the newspaper metaphor: short top-level methods delegate to named private helpers
- ArchUnit enforces layering: `persistence` must not access `logic` or `controller`

---

## Package Structure

```
org.goafabric.centerstage

Application.kt                        @QuarkusMain entry point

extensions/
  ExceptionHandler.kt                 Maps NoSuchElementException → 404, others → 500

catalog/
  controller/
    ComponentController.kt            GET /api/catalog/components, /components/{name}
    ApiController.kt                  GET /api/catalog/apis, /apis/{name}/spec,
                                        /components/{name}/apis, /components/{name}/api-spec
    AdrController.kt                  GET /api/catalog/components/{name}/adrs
    DocsController.kt                 GET /api/catalog/components/{name}/docs,
                                        /components/{name}/docs/assets/{assetPath}
    GraphController.kt                GET /api/catalog/components/{name}/graph
    dto/
      Component.kt                    name, owner, type, lifecycle, description, tags, links, annotations, providesApis
      Api.kt                          name, type, lifecycle, description, definitionUrl
      Adr.kt                          name, content (markdown)
      TechDoc.kt                      name, content (markdown)
      Graph.kt                        Graph, GraphNode, GraphEdge

  logic/
    CatalogLoaderLogic.kt             Parses YAML into ComponentEo list (in-memory, not persisted here)
                                        Resolves spec.targets[], definition.$text URLs, GitLab API URLs
    CatalogIngestionLogic.kt          @Observes StartupEvent + @Scheduled: calls loader, persists
                                        ComponentEo/AdrEo/DocEo; fetches ADRs + docs from GitHub/GitLab
    ComponentLogic.kt                 getComponents(), getComponent()
    ApiLogic.kt                       getAllApis(), getApis(), getApiSpec(), getApiSpecByName()
    AdrLogic.kt                       getAdrs() — DB → remote (GitHub/GitLab) → local files fallback chain
    DocsLogic.kt                      getDocs(), getDocsAssetFile() — same fallback chain
    GraphLogic.kt                     getGraph() — focus node + one-hop neighbours from DB
    mapper/
      CatalogMapper.kt                MapStruct: ComponentEo→Component, ComponentEo→Api,
                                        AdrEo→Adr, DocEo→TechDoc; @Named helpers for comma-split fields

  persistence/
    ComponentRepository.kt            PanacheRepository.Managed<ComponentEo, String>
                                        findByKind, findByKindAndName, search (LIKE on searchText)
    AdrRepository.kt                  PanacheRepository.Managed<AdrEo, String>
                                        findByComponentName, search
    DocRepository.kt                  PanacheRepository.Managed<DocEo, String>
                                        findByComponentName, search
    entity/
      ComponentEo.kt                  All catalog kinds (Component, API, Resource) — flat columns,
                                        comma-separated lists, annotation(key)/splitList() helpers
      AdrEo.kt                        componentName, name, content, searchText
      DocEo.kt                        componentName, name, content, searchText

  adapter/
    RemoteContentService.kt           Single outbound HTTP entry point:
                                        toRawUrl() — GitHub blob → raw, GitLab blob → API URL
                                        isGitLabUrl() — detects /-/ or /api/v4/
                                        fetchText() / fetchTextOrNull() — with PRIVATE-TOKEN for GitLab
    GitHubAdapter.kt                  @RegisterRestClient — GitHub Contents API (listContents)
    GitHubService.kt                  fetchAdrs(treeUrl) → List<AdrEo>
                                        fetchDocs(rawCatalogUrl, techDocsRef) → List<TechDoc>
    GitLabAdapter.kt                  @RegisterRestClient — GitLab API v4
                                        listTree / getRawFile — @Encoded on {id} and {filePath}
    GitLabService.kt                  fetchAdrs(treeUrl) → List<AdrEo>; accepts /-/tree/ and /-/blob/
                                        fetchDocs(catalogUrl, techDocsRef) → List<TechDoc>
                                        accepts /-/raw/, /-/blob/, /api/v4/…/raw?ref= URL formats

techradar/
  controller/
    TechRadarController.kt            GET /api/techradar — proxies configured JSON URL
```

---

## REST API

| Method | Path | Description |
|---|---|---|
| GET | `/api/catalog/components` | All `kind: Component` entries |
| GET | `/api/catalog/components/{name}` | Single component (404 if not found) |
| GET | `/api/catalog/components/{name}/apis` | APIs provided by component |
| GET | `/api/catalog/components/{name}/api-spec` | OpenAPI spec proxied server-side (JSON or YAML) |
| GET | `/api/catalog/components/{name}/adrs` | ADRs (DB → GitHub/GitLab → local fallback) |
| GET | `/api/catalog/components/{name}/docs` | TechDocs pages (DB → GitHub/GitLab → local fallback) |
| GET | `/api/catalog/components/{name}/docs/assets/{assetPath}` | Static doc asset from local disk |
| GET | `/api/catalog/components/{name}/graph` | Dependency graph (focus node + direct neighbours) |
| GET | `/api/catalog/apis` | All `kind: API` entries |
| GET | `/api/catalog/apis/{name}/spec` | OpenAPI spec for named API, proxied server-side |
| GET | `/api/techradar` | Tech radar JSON (proxied from configured URL) |

---

## Persistence

- **DB**: H2 in-memory (dev/test), PostgreSQL (prod)
- **Schema**: managed by Flyway (`src/main/resources/db/migration/V1__create_catalog_tables.sql`)
- **Tables**: `component_eo`, `adr_eo`, `doc_eo`
- **Ingestion**: triggered at startup (`@Observes StartupEvent`) and on schedule (`@Scheduled`)
- **Search**: `LIKE %query%` on the `search_text` column (pre-built at ingestion time)
- `ComponentEo` stores all catalog kinds (Component, API, Resource) in one table; lists are comma-separated strings; `annotation(key)` / `splitList()` helpers on the entity

---

## Catalog Parsing

- Entry point: configured YAML file — `kind: Location` with `spec.targets[]`
- Each target: one or more YAML documents separated by `---`
- `kind: Component` → component table, overview, graph
- `kind: API` → API table; `spec.definition.$text` resolved to a GitLab API raw URL at load time
- `kind: Resource` → graph nodes only
- Remote targets: GitHub blob and GitLab blob/raw URLs normalised to raw/API URLs at load time

### ADR resolution order (AdrLogic)
1. DB — `adrRepo.findByComponentName()`
2. `annotations["backstage.io/adr-location"]` starts with `https://github.com` → GitHub Contents API
3. `annotations["backstage.io/adr-location"]` is a GitLab URL → GitLab tree API (`/-/tree/` or `/-/blob/`)
4. Local fallback: `adr/{component-name}/` relative to catalog file (local catalog only)

### TechDocs resolution order (DocsLogic)
1. DB — `docRepo.findByComponentName()`
2. `sourcePath` is `raw.githubusercontent.com` → GitHub API
3. `sourcePath` is a GitLab URL → GitLab API; `techdocs-ref: dir:.` resolves `docs/` relative to catalog-info.yaml
4. Local fallback: `docs/` next to catalog-info.yaml; nav order from `mkdocs.yml` if present

### OpenAPI spec proxying
- `definitionUrl` requires `PRIVATE-TOKEN` — browser cannot send it cross-origin
- Two server-side proxy endpoints:
  - `/components/{name}/api-spec` — first openapi-typed API of the component
  - `/apis/{name}/spec` — standalone API by name
- Content-type auto-detected: JSON (`{`) or YAML

---

## Frontend (`src/main/resources/META-INF/resources/`)

Single-page app with `window.location.hash` routing. No framework.

| File | Route | Description |
|---|---|---|
| `index.html` | — | Layout (sidebar 20% / content 80%), router, nav |
| `styles.css` | — | All styles |
| `components/catalog.js` | `#catalog` | Component table: NAME, OWNER, TYPE, LIFECYCLE, DESCRIPTION, TAGS |
| `components/overview.js` | `#component/{name}` | Detail card; tabs: Overview / API / ADR / Docs / Graph |
| `components/api-view.js` | `#component/{name}/api` | SwaggerUI (CDN) → `/components/{name}/api-spec` proxy |
| `components/adr.js` | `#component/{name}/adr` | Left nav + marked.js markdown |
| `components/docs.js` | `#component/{name}/docs` | TechDocs viewer + global Docs index; images via `/docs/assets/` |
| `components/graph.js` | `#component/{name}/graph` | Cytoscape.js interactive dependency graph |
| `components/apis.js` | `#apis` | All APIs table; clicking name → SwaggerUI via `/apis/{name}/spec` |
| `plugins/techradar.js` | `#techradar` | Zalando radar.js + D3 v7 tech radar |

### Graph
- Shapes: roundrectangle = component, cylinder = resource, diamond = API
- Colours: dark/purple = focus, blue = component, amber = resource, green = API
- Edges: red = dependsOn, purple = dependencyOf, green = providesApis
- Scope: focus node + direct neighbours (one hop); click node → navigate to component

---

## Tests (`src/test/`)

| Class | Type | Coverage |
|---|---|---|
| `ComponentControllerIT` | Integration (`@QuarkusTest`) | GET /components, /components/{name}, 404, /apis, /adrs |
| `ComponentLogicTest` | Unit (Mockito, mocks `ComponentRepository`) | getComponents, getComponent, 404, getApis, getApis 404 |
| `CatalogRepositoryIT` | Integration (`@QuarkusTest`, `@Transactional`) | findByKind, findByKindAndName, search, ADR/Doc repo, CatalogMapper mapping |
| `ArchitectureTest` | ArchUnit | persistence → no logic/controller; logic (excl. mapper) → no controller package |

**Test profile** (`src/test/resources/application.properties`):
- Uses GitHub catalog URL (not local files)
- `quarkus.scheduler.enabled=false` — prevents ingestion scheduler from running during tests

---

## Key GitLab Integration Notes

- **Double-encoding prevention**: `GitLabAdapter` uses `@Encoded` on `@PathParam("id")` and `@PathParam("filePath")` so the REST client does not re-encode `%2F` separators
- **URL normalisation**: All blob URLs → GitLab API raw URLs at load time via `RemoteContentService.toRawUrl()`
- **ADR blob URLs**: `GitLabService.parseTreeUrl()` accepts both `/-/tree/` and `/-/blob/`
- **TechDocs URL formats**: `GitLabService.parseCatalogUrl()` accepts `/-/raw/`, `/-/blob/`, and `/api/v4/projects/{id}/repository/files/{path}/raw?ref={ref}`
- **Definition URL resolution**: relative `$text` paths under GitLab API URLs are decoded, re-pathed against the catalog directory, and re-encoded correctly

---

## Key Files for Session Resumption

- `AGENTS.md` — this file
- `doc/inst.md` — architectural requirements and conventions
- `src/main/resources/application.properties` — all runtime config
- `src/main/resources/db/migration/V1__create_catalog_tables.sql` — DB schema
