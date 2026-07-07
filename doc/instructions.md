# basics
- please create a new service with quarkus
- follow the build.gradle.kts inside `./spec/quarkus/build.gradle.kts`
- follow the example service files inside the zipped file, which needs to be unzipped `./spec/quarkus/example.zip`
- follow technical requirements inside `./spec/quarkus/technical-requirements.md`
  
# scope
- scope of the new service is a backstage like application, see https://backstage.io/
- you can create all files inside the ./centerstage folder
- it should be just way simpler with just quarkus, and a simple frontend with javascript, html, css
- in the first step simply served from quarkus resources/META-INF/resources folder
- still there should be a clean separation in multiple html /javascript files where required

# requirements
- it should be apple to parse and understand backstage catalog-info.yaml
- multiple files should be read from an overarching file, in our case ./centerstage/doc/catalog/entities-local.yaml  
- this file should be configurable via application.properties
- in the first step anything persistence is most likely not required if the digested infos can be held in memory
- if you think storing into a database makes sense already, you may use quarkus with H2 + repository pattern as found in the example.zip

# design of the navigation
- to the left we should have a separated navigation, approx 20% of the width
- for not it should containg one entry "catalog", that when selected shows the component view
- you could also add 2 other, for now non working entries APIS + DOCS

# design of component view             
- the design of the ui should be clear, clean, modern and simple
- in the first step its enough to have one page to the right, approx 80% of the width,  that list all components found in the catalog-info.yaml 
- and their content with columns: NAME, OWNER, TYPE, LIFECYCLE, DESCRIPTION, TAGS
- the tags should be rounded labels/bubbles

# design of the overview card
- when clicking the name of one of the components from commponents view, it should open a new view "OVERVIEW"
- it should basically display the main info from the table column, just card like
- it could also contain additional label images from the catalog-info that need to be rendered
- e.g [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=org.goafabric%3Acallee-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=org.goafabric%3Acallee-service)

# design of the api card
- as a second register card besides overview, we should have "API"
- this if simply possible should this display the OPENAPI found, it should be interactive like it would also be in GITHUB/GITLAB

# design of the ADR card
- if the component containds ADR, architecture decission records they should be on this third card
- to the left we have again navigation listing all ADRs by name
- to the right the ADR itself, which basically is a rendered MARKDOWN
- the ADRs should be selectable from the navigation

# approach for you as implementor

- analyze the technical and functinoal requirements
- if requirement create a plan for review
- and then also take care that the session could be resumed later if possible
- 