# API in Navigation bar
- for the API inside the Navigation BAR, when clicked, show to the right all api names in table (e.g catalog.api)
- the right view should again have these columns: NAME, OWNER, TYPE, LIFECYCLE, DESCRIPTION, TAGS
- when a name is clicked the already implemented OpenAPI view should be shown for the API

# Tech Radar in Navigation
- inside the Navigation Bar we should have another entry Tech Radar
- when clicked on the right the Tech Radar should be shown
- this is based on this one https://github.com/zalando/tech-radar
- it should be configurable on app.properties via techradar.url
  - as an example: "https://github.com/goafabric/example-konstruction-kit/blob/develop/helm/infra/techradar/data/config-backstage.json"

# graph card
- please add another registercard in the components view
- this one is called "GRAPH" and should well display an if possible interactive graph that shows the relations between the components
- as an example see e.g.
  spec:
  type: service
  lifecycle: production
  owner: team-blue
  providesApis:
  - core-api
  - organization-topic
    dependsOn:
  - resource:core-db
  - resource:kafka
    dependencyOf:
  - component:api-gateway

# techdocs
- backstage has the concept of "techdocs" which is essentially markdown, but it can also contain images
- you can find an example in "./doc/catalog/guidelines"
- they can be included optionally by "catalog-info.yaml"
- please have another registercard "DOCS" in the Components View, that can display these
- in the the sidebar to the lef wie already have "Docs", here similar to the other sidebar actions all docs should be listed and viewed upon click
-
