# description
- mimicry of backstage's most important functionality
- reads structure of backstage catalog-info.yaml either via gihub or gitlab, for gitlab extra token is required
- implemented with quarkus native and plain javascript inside src/main/resource
- vibe coded with markdown files inside doc folder

# docker compose
go to /src/deploy/docker and do "./stack up"

# run native image
container image pull goafabric/centerstage:$(grep '^version=' gradle.properties | cut -d'=' -f2)
"${(@z)${CRUNTIME:-docker run --pull always}}" --name centerstage --rm -p 50700:50700 goafabric/centerstage:$(grep '^version=' gradle.properties | cut -d'=' -f2) 

docker run --pull always --name centerstage --rm -p 50700:50700 goafabric/centerstage:1.0.2-SNAPSHOT