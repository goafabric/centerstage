# centerstage

A Backstage-inspired developer portal built with Quarkus 3.37.0 + Kotlin + plain HTML/JS/CSS.
It parses Backstage `catalog-info.yaml` files and renders a clean web UI for browsing components,
APIs, architecture decision records, an interactive dependency graph, TechDocs, and a tech radar.

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
| `techradar.url` | *(GitHub JSON URL)* | URL to tech radar JSON (GitHub blob URL supported) |
| `github.token` | *(empty)* | GitHub API token; falls back to `GITHUB_TOKEN` env var |
| `gitlab.token` | *(empty)* | GitLab API token; falls back to `GITLAB_TOKEN` env var |
| `quarkus.rest-client.GitHubAdapter.url` | `https://api.github.com` | GitHub REST client base URL |
| `quarkus.rest-client.GitLabAdapter.url` | *(self-hosted host)* | GitLab REST client base URL |
| `quarkus.http.port` | `50700` | HTTP port |

---

## Package Structure

```
org.goafabric.centerstage

Application.kt                        @QuarkusMain entry point

extensions/
  ExceptionHandler.kt                 Maps NoSuchElementException → 404, others → 500

catalog/                              Domain package
  controller/
    CatalogController.kt              REST @Path("/api/catalog") — all catalog endpoints
    dto/
      Component.kt                    name, owner, type, lifecycle, description, tags, links, annotations, providesApis
      Api.kt                          name, type, lifecycle, description, definitionUrl
      Adr.kt                          name, content (markdown)
      TechDoc.kt                      name, content (markdown)
      Graph.kt                        Graph, GraphNode, GraphEdge

  logic/
    CatalogLoaderLogic.kt             @ApplicationScoped — loads catalog on startup (@Observes StartupEvent):
                                        reads entry-point YAML, resolves spec.targets[], parses all YAML docs
                                        into CatalogEo list held in memory; resolves definition.$text URLs
                                        (handles GitLab API URL format for relative paths)
    CatalogLogic.kt                   Business logic: getComponents, getComponent, getAllApis, getApis,
                                        getAdrs, getGraph, getDocs, getDocsAssetFile,
                                        getApiSpec (proxy for component's OpenAPI),
                                        getApiSpecByName (proxy for standalone API entry)
    mapper/
      CatalogMapper.kt                MapStruct mapper: CatalogEo → Component / Api / Adr / TechDoc

  persistence/
    entity/
      CatalogEo.kt                    kind, MetadataEo, SpecEo, sourcePath (absolute path of loaded catalog-info.yaml)
      AdrFileEo.kt                    name, content (for local ADR markdown files)
      MetadataEo.kt                   name, description, tags, annotations, links
      SpecEo.kt                       type, lifecycle, owner, providesApis, dependsOn, dependencyOf, definition
      DefinitionEo.kt                 text ($text field — resolved to raw fetchable URL at load time)
      LinkEo.kt                       url, title

  adapter/
    RemoteContentService.kt           Single outbound HTTP entry point:
                                        toRawUrl()       — converts GitHub blob / GitLab blob URLs to raw/API URLs
                                        gitLabBlobToApiUrl() — blob → GitLab API v4 raw file URL
                                        isGitLabUrl()    — detects GitLab URLs (/-/ or /api/v4/)
                                        fetchText()      — HTTP GET with PRIVATE-TOKEN header for GitLab
                                        fetchTextOrNull() — same, returns null on failure
    GitHubAdapter.kt                  @RegisterRestClient(configKey="GitHubAdapter") — GitHub Contents API
                                        listContents(owner, repo, path, ref, token, userAgent)
    GitHubService.kt                  fetchAdrs(treeUrl) — list + fetch .md files from GitHub tree URL
                                        fetchDocs(rawCatalogUrl, techDocsRef) — fetch docs/ from GitHub
    GitLabAdapter.kt                  @RegisterRestClient(configKey="GitLabAdapter") — GitLab API v4
                                        listTree(projectId, path, ref, perPage, token) — uses @Encoded on {id}
                                        getRawFile(projectId, filePath, ref, token)   — uses @Encoded on {id},{filePath}
    GitLabService.kt                  fetchAdrs(treeUrl) — list + fetch .md from GitLab tree or blob URL
                                        fetchDocs(catalogUrl, techDocsRef) — fetch docs/ from GitLab;
                                          accepts /-/raw/, /-/blob/, and /api/v4/projects/…/files/…/raw URLs
                                        parseTreeUrl() — parses /-/tree/ and /-/blob/ URLs
                                        parseCatalogUrl() — parses raw, blob, and API URL formats

techradar/
  controller/
    TechRadarController.kt            REST @Path("/api/techradar") — fetches + proxies tech radar JSON
```

---

## REST API

| Method | Path | Description |
|---|---|---|
| GET | `/api/catalog/components` | All `kind: Component` entries |
| GET | `/api/catalog/components/{name}` | Single component (404 if not found) |
| GET | `/api/catalog/components/{name}/apis` | APIs provided by component |
| GET | `/api/catalog/components/{name}/api-spec` | OpenAPI spec proxied server-side (JSON or YAML) |
| GET | `/api/catalog/components/{name}/adrs` | ADRs (GitHub, GitLab, or local files) |
| GET | `/api/catalog/components/{name}/docs` | TechDocs markdown pages |
| GET | `/api/catalog/components/{name}/docs/assets/{assetPath}` | Static doc asset (image etc.) from local disk |
| GET | `/api/catalog/components/{name}/graph` | Dependency graph (focus node + direct neighbours) |
| GET | `/api/catalog/apis` | All `kind: API` entries |
| GET | `/api/catalog/apis/{name}/spec` | OpenAPI spec for a named API, proxied server-side |
| GET | `/api/techradar` | Tech radar JSON (proxied from configured URL) |

---

## Catalog Parsing

- Entry point: `entities-local.yaml` (or a remote URL) — `kind: Location` with `spec.targets[]`
- Each target: one or more YAML documents separated by `---`
- `kind: Component` → components table, overview, graph
- `kind: API` → APIs table, linked from components via `spec.providesApis`; `spec.definition.$text` resolved to a raw URL at load time
- `kind: Resource` → graph nodes only
- Remote targets: GitHub blob and GitLab blob/raw URLs are normalised to fetchable raw/API URLs at load time
- `spec.definition.$text` relative paths: resolved against the catalog-info.yaml's directory using the GitLab API URL structure (re-encodes path segments correctly)

### ADR resolution order
1. `annotations["backstage.io/adr-location"]` starts with `https://github.com` → GitHub Contents API
2. `annotations["backstage.io/adr-location"]` is a GitLab URL (`/-/` or `/api/v4/`) → GitLab tree API (accepts `/-/tree/` and `/-/blob/` URLs)
3. Local fallback: `doc/catalog/adr/{component-name}/` (only when catalog is a local file)

### TechDocs resolution order
1. `sourcePath` is `raw.githubusercontent.com` → GitHub API (`gitHubService.fetchDocs`)
2. `sourcePath` is a GitLab URL → GitLab API (`gitLabService.fetchDocs`); `techdocs-ref: dir:.` resolves `docs/` relative to catalog-info.yaml directory
3. Local fallback: `docs/` directory next to catalog-info.yaml; nav order from `mkdocs.yml` if present

### OpenAPI spec proxying
- The `definitionUrl` on an API entry is a GitLab API URL requiring a `PRIVATE-TOKEN` header
- The browser cannot send this header cross-origin, so two proxy endpoints fetch server-side:
  - `/components/{name}/api-spec` — for component view (first openapi-typed API)
  - `/apis/{name}/spec` — for standalone API view
- Content-type auto-detected: JSON (`{`) or YAML

---

## Frontend (`src/main/resources/META-INF/resources/`)

Single-page app with `window.location.hash` routing. No framework.

| File | Route | Description |
|---|---|---|
| `index.html` | — | Layout (sidebar 20% / content 80%), router, nav |
| `styles.css` | — | All styles — dark sidebar, cards, tables, tags, graph legend |
| `components/catalog.js` | `#catalog` | Component table: NAME, OWNER, TYPE, LIFECYCLE, DESCRIPTION, TAGS |
| `components/overview.js` | `#component/{name}` | Detail card + badge images; tabs: Overview / API / ADR / Docs / Graph |
| `components/api-view.js` | `#component/{name}/api` | SwaggerUI (CDN) pointed at `/components/{name}/api-spec` proxy |
| `components/adr.js` | `#component/{name}/adr` | Left nav + marked.js markdown rendering |
| `components/docs.js` | `#component/{name}/docs` | TechDocs viewer: left nav + marked.js; image assets via `/docs/assets/` endpoint; also renders global Docs index |
| `components/graph.js` | `#component/{name}/graph` | Cytoscape.js interactive dependency graph |
| `components/apis.js` | `#apis` | All APIs table; clicking name opens SwaggerUI via `/apis/{name}/spec` proxy |
| `plugins/techradar.js` | `#techradar` | Zalando radar.js + D3 v7 tech radar visualization |

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

## Key GitLab Integration Notes

- **Double-encoding prevention**: `GitLabAdapter` uses `@Encoded` on `@PathParam("id")` and `@PathParam("filePath")` so the MicroProfile REST client does not re-encode already-encoded `%2F` separators in project IDs and file paths.
- **URL normalisation**: All remote blob URLs are converted to GitLab API raw URLs at catalog load time via `RemoteContentService.toRawUrl()` / `gitLabBlobToApiUrl()`.
- **ADR blob URLs**: `GitLabService.parseTreeUrl()` accepts both `/-/tree/` and `/-/blob/` path markers.
- **TechDocs catalog URL formats**: `GitLabService.parseCatalogUrl()` accepts `/-/raw/`, `/-/blob/`, and `/api/v4/projects/{id}/repository/files/{path}/raw?ref={ref}`.

---

## Key Files for Session Resumption

- `centerstage/AGENTS.md` — this file
- `centerstage/doc/catalog/entities-local.yaml` — local catalog entry point (used in test profile)
- `src/main/resources/application.properties` — all runtime config including GitLab/GitHub REST client URLs
