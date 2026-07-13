# docker compose
go to /src/deploy/docker and do "./stack up"

# run native image
container image pull goafabric/centerstage:$(grep '^version=' gradle.properties | cut -d'=' -f2)
"${(@z)${CRUNTIME:-docker run --pull always}}" --name centerstage --rm -p 50700:50700 -e 'github.token=""' goafabric/centerstage:$(grep '^version=' gradle.properties | cut -d'=' -f2) 

docker run --pull always --name centerstage --rm -p 50700:50700 -e 'github.token=""' goafabric/centerstage:1.0.0-SNAPSHOT