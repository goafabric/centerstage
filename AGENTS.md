# centerstage

A Backstage-inspired developer portal built with Quarkus 3.37.0 + Kotlin + plain HTML/JS/CSS.
It parses Backstage `catalog-info.yaml` files and renders a clean web UI for browsing components,
APIs, architecture decision records, an interactive dependency graph, and a tech radar.

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
| `centerstage.catalog.file` | `doc/catalog/entities-local.yaml` | Entry-point catalog file |
| `techradar.url` | *(GitHub JSON URL)* | URL to tech radar JSON (GitHub blob URL supported) |
| `github.token` | *(empty)* | GitHub API token; falls back to `GITHUB_TOKEN` env var |
| `quarkus.http.port` | `50700` | HTTP port |

---

## Package Structure

```
org.goafabric.centerstage

Application.kt                        @QuarkusMain entry point

extensions/
  ExceptionHandler.kt                 Maps exceptions to HTTP status codes

catalog/                              Domain package
  controller/
    CatalogController.kt              REST @Path("/api/catalog")
    dto/
      Component.kt                    name, owner, type, lifecycle, description, tags, links, annotations, providesApis
      Api.kt                          name, type, lifecycle, description, definitionUrl
      Adr.kt                          name, content (markdown)
      Graph.kt                        Graph, GraphNode, GraphEdge
  logic/
    CatalogLogic.kt                   Business logic: getComponents, getComponent, getAllApis, getApis, getAdrs, getGraph
    mapper/
      CatalogMapper.kt                MapStruct mapper: CatalogEo → Component / Api / Adr
  persistence/
    CatalogLoader.kt                  @PostConstruct: reads entities-local.yaml, resolves targets, parses YAML into memory
    entity/
      CatalogEo.kt                    kind, MetadataEo, SpecEo (type, lifecycle, owner, providesApis, dependsOn, dependencyOf, definition)
      AdrFileEo.kt                    name, content (for local ADR files)
  adapter/
    GitHubAdapter.kt                  @RegisterRestClient — GitHub Contents API (list directory)
    GitHubService.kt                  Converts GitHub tree URLs → ADR file list via API + raw fetch

techradar/
  controller/
    TechRadarController.kt            REST @Path("/api/techradar") — fetches + proxies configured JSON URL
```

---

## REST API

| Method | Path | Description |
|---|---|---|
| GET | `/api/catalog/components` | All `kind: Component` entries |
| GET | `/api/catalog/components/{name}` | Single component (404 if not found) |
| GET | `/api/catalog/components/{name}/apis` | APIs provided by component |
| GET | `/api/catalog/components/{name}/adrs` | ADRs (local files or fetched from GitHub) |
| GET | `/api/catalog/components/{name}/graph` | Dependency graph (focus-scoped nodes + edges) |
| GET | `/api/catalog/apis` | All `kind: API` entries |
| GET | `/api/techradar` | Tech radar JSON (proxied from configured URL) |

---

## Catalog Parsing

- Entry point: `entities-local.yaml` — `kind: Location` with `spec.targets[]` (relative file paths)
- Each target: one or more YAML documents split by `---`
- `kind: Component` → components table, overview, graph
- `kind: API` → APIs table, linked from components via `spec.providesApis`
- `kind: Resource` → graph nodes only
- ADR resolution:
  1. If `annotations["backstage.io/adr-location"]` starts with `https://github.com` → fetch via GitHub Contents API
  2. Otherwise → look in `doc/catalog/adr/{component-name}/` locally
- Badge images in `metadata.description` use markdown syntax: `[![alt](imgUrl)](linkUrl)`

---

## Frontend (`src/main/resources/META-INF/resources/`)

Single-page app with `window.location.hash` routing. No framework.

| File                    | Route | Description |
|-------------------------|---|---|
| `index.html`            | — | Layout (sidebar 20% / content 80%), router, nav |
| `styles.css`            | — | All styles — dark sidebar, cards, tables, tags, graph legend |
| `components/catalog.js` | `#catalog` | Component table: NAME, OWNER, TYPE, LIFECYCLE, DESCRIPTION, TAGS |
| `components/overview.js`           | `#component/{name}` | Detail card + badge images; tabs: Overview / API / ADR / Graph |
| `components/api-view.js`           | `#component/{name}/api` | Swagger UI (CDN) for component's OpenAPI spec |
| `components/adr.js`                | `#component/{name}/adr` | Left nav + marked.js markdown rendering |
| `components/graph.js`              | `#component/{name}/graph` | Cytoscape.js interactive dependency graph |
| `components/apis.js`               | `#apis` | All APIs table; clicking name opens Swagger UI |
| `techradar.js`          | `#techradar` | Zalando radar.js + D3 v7 tech radar visualization |

### Graph details
- Node shapes: roundrectangle = component, cylinder = resource, diamond = API
- Node colours: dark/purple = focus, blue = component, amber = resource, green = API
- Edge colours: red = dependsOn, purple = dependencyOf, green = providesApis
- Scope: focus node + direct neighbours only (one hop)
- Click node → navigate to that component's overview
- Click node → highlights neighbourhood, fades rest

---

## Tests (`src/test/`)

| Class | Type | Coverage |
|---|---|---|
| `CatalogControllerIT` | Integration (`@QuarkusTest`) | GET /components, GET /components/{name}, 404, /apis, /adrs |
| `CatalogLogicTest` | Unit (Mockito) | getComponents, getComponent, getComponent unknown, getApis, getApis unknown |
| `ArchitectureTest` | ArchUnit | persistence → no logic/controller; logic (excl. mapper) → no controller layer classes |

---

## Key Files for Session Resumption

- `centerstage/PLAN.md` — original design plan with full context
- `centerstage/AGENTS.md` — this file
- `spec/quarkus/build.gradle.kts` — reference build template
- `spec/quarkus/technical-requirements.md` — project architecture rules
- `centerstage/doc/catalog/entities-local.yaml` — catalog entry point
- `centerstage/doc/catalog/example/callee-service/catalog-info.yaml` — example catalog file
- `centerstage/doc/catalog/adr/` — local ADR markdown files
