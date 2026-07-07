# Backend

The PersonService serves as a small example on how to build a JPA Services with Spring Boot.
It includes the following concerns, which are separated in layers.
It also follows a pragmatic "hexagonal architecture", by decoupling most of the layers by interfaces.

- Controller: REST API via MVC RestController
- Persistence: Spring Data JPA to access the Database
  - Mapstruct to map the database Entities to REST DTOs
- Adapter: Connect to other Services via Declarative REST Clients

# General
- Usage of Lombok is strongly discouraged, as there are now far superior options, see sections below.
- Usage of Reflection is strongly discouraged, as it will break native images, make the code very hard to understand and imposes additional attack vectors.#
- Usage of RestTemplate or WebClient is discouraged. For imperative Rest clients the way to go is now the declarative RestCLIENT.

## Basic Layers
### Controller 
 
The Controller is a thin layer for the REST API via Spring WEB Annotations.
With Quarkus similar Results can be achieved by leveraging JaxRS annotations.

Then Controller directly than delegates to the logic layer.
This way the Controller could be swapped out for other inbound communication protocols,
like GraphQl or Message Broker Protocols.

The DTOs are defined as Java Records.
Making it easy to create immutable objects, with Getters/Setters/Equals Hashcode, without the Burden of Lombok.
In contrast to Kotlin or the Builder Pattern, there is however the drawback that the Records construct cannot have
named Arguments.

For simple CRUD applications, like most of our applications should be,
it is also encouraged to go with a single data structure that contains ID and Version as optional fields. 
of going for 2 separate Structures, just for the initial Creation of the Object.
This leads to simpler, more consistent code, less redundant code, without filler words like request/response.

### Logic

The logic is the layer that ... well implements the business logic.
It also connects to the database via repositories.
Or external services via adapters.

To map the data from repositories to the controller, mapstruct is leveraged.
Mapstruct has the clear advantage over other mapping frame works, that use reflection,
that compile safe code is generated during the build phase
                                                                
### Adapter

The Adapter connects to other rest services.
From Spring Boot 3.2.0 on ... it is now finally possible to leverage a Declarative, non reactive REST Client.
Quarkus and the Jakarta EE stack have concepts for this for years now ...
The declarative approach eliminates the requirement of tediously writing your own "RestTemplate" adapters.
It will just require an interface, with annoations simmilar (!= equal) to Spring MVC.
And a Factory Class that creates and configures these interfaces.
Also authentication / setting http headers to other backend services will go to the configuration class.
    
## Persistence Layer 
### Repository

The Repository Layer leverages Spring Data JPA to communicate with the database.
The repository abstraction just defines Method names like "findByFirstName", that then will be automatically converted
to SQL commands.
For more fine grained control, @Query can be leveraged to define JPQL queries on your own.
Please keep in mind that JQPL might look like SQL, but it is not SQL.
Usage of Native SQL Queries, which is also possible, is discouraged.
As it breaks the abstraction and defeats concerns like Multi Tenancy, also provided by the Framework.

With Spring Data in places it is also theoretically possible, to replaces Spring Data JPA,
with something like Spring Data Mongo ... to even replaces postgres with a mongodb.
However these abstractions usually only work for a limited set of CRUD operations.
                    
### Performance
For performance reasons please keep in mind the n+1 problems when working with jpa/hibernate.
In default mode, hibernate will create a separate select for each row inside the n relation.
For large result sets (> 100) this will very easily introduce a performance problem.
There are solutions like @EntityGraph for this, that unfortunately could also impose side effects.

As a rule of thumb, queries should return in < 100 ms ... and you should always activate logging of the SQLs.
"logging.level.org.hibernate.SQL: "DEBUG" inside application.yml.

### Entities
Entities are just defined as JPA Entities.
Unfortunately we cannot leverage Java Records here, as JPA Entities have to be mutable.
Also Mapstruct needs to be able to access and construct the Entities without reflection.

We could opt for Lombok here, but with the introduction of Java Records, the usage of Lombok has started to decline.
Please also know, that Usage of @Data / @EqualsHashcode is discouraged for JPA Entities.

Again, Kotlin has a solution for the whole dilemma ... with java it is more complicated.
So we are opting here with an immutable approach, also leveraged by most other languages.
The Entities contain a FullArgs Constructor + Getters, No Setters.
These have to be generated via the IDE.

### Multi Tenancy by Schema
Multi Tenancy is taken care of by the "TenantResolver" in Tandem with the "HttpInterceptor".
While the HttpInterceptor is reponsible to retrieve the TenantId from the Frontend,
the TenantResolver is repsonsible to retrieve the tenant specific data from the database.
The TenantResolver is as standard Hibernate mechanism that can be leveraged to implement MT by schema or discriminator.

Schema implementation works by overriding a method that just switches the schema on the connection, by tenant.
Discriminator implementation works by just setting an @TenantId at the Entity level.

When opting for schema, this also means that the Flyway Database Scripts have to be processed for every schema.
The TenantResolver has an easy mechanism for this, that requires all Tenants to be configured inside the application.yml.
This configuration will in production by injected from the Kubernetes Environment.

Schema Updates can be activated with "-migrate".
The idea is to start the service if an update is required with "-migrate", in a "provision mode"
But during the usually lifececle inside Kubernetes, it will always start WITHOUT "-migrate", 
making it possible start and scale services fast.

And Alternative could be to move all flyway scripts outside of the services and let the operations team handle it.
But this will not only artificially separate Code and Database Scripts ... 
It will also make local Development of the Applications more tedious.

The 3rd option would be to do both ... having the scripts inside the Application, for Development Mode.
And let the operations team still take care of the while process, by just copying the Scripts and disable the "-migrate" in production.
            
While Multi Tenancy by Schema has the Drawback of having to update multiple schemas, it has the following clear advantages:
- Clear Abstraction of the Tenant Data, also making it easier to fulfill GDPR and C4 requirements
- Deletion or Carve out of separate Customers is much easier in this scenario, than with a discriminator
- Having 100s of Tenants inside the Same Table by Discriminator, could lead easy lead full table scans of the DB 
                                                                                    
### Organization by Discriminator
In the case of our application, where we need a Tenant, representing the customer, and an Organization,
which is a sub group below the customer ... we need both.
This is why besides the TenantResolver the PersonEo also carries a @TenantId for the organization.
For 1:n entities it has to be decided if the Organization Descriminator is sufficient inside the 1 relation
or also should go to the :n relation.
Both is possible, as both entities are linked by unique UUIDs.
While the first approach has to advantage of less "polution" the tables, it has to be verified if there is an import an the performance.
                 
### Auditing
Auditing is a GDPR constraint that roughly requires for each person related data change to be "audited".
Which means to have a protocol table, that stores every CRUD Operation including the Data change.
And also the "person" that did the change.

For Auditing there are well known frameworks like https://javers.org/.
Unfortunately while the integrate easy and nicely with Spring Data,
the Excessive Usage of Reflection brakes Native Image Support and is also a little bit problematic.
Additionally Multi Schema Support is not supported out of the box and has to be verified.

For that reason we have a simple AuditTrailListener Custom Code here, that writes to a single table like Javers.
If however the Drawbacks mentioned above can be solved or mitigated, Javers could be a superior choice.

### Demo Data
Demo Data is imported by a simple DemoDataImporter Class, that again has to be activated by a swith "-import-demo-data".
While is also possible to just have Demodata as scripts inside the Database, avoiding this custom code.
Handling with pure Code is much more natural, verifies the existing APIs and also makes it much easer to deal with multi tenancy.
     

## Cross Cutting Concstraints
### Security

In this small example for Authentication we just leverage Basic Authentication.
For production ready applications, more sophisticated solutions like OpenID Connect are usually required.
As we are leveraging Spring Security currently here, this mostly means to add another spring security dependency here
to opt for OpenID Connect.
There could be other examples to showcase this further.

Our final goal however, is that authentication will be mostly handle by the API Gateway outside the application.
And that the application will just retrieve all required information (user, tenant, organization) from a supplied JWT.

### ExceptionHandler

The ExceptionHandler is a standard Spring Exception Handler, that catches all exceptions at a central place.
And also maps it to an HTTP Code and logs the exceptions

### Monitoring
Spring supplies standard solutions for enabling monitoring solutions (like Grafana OS) inside a container world.
Most of these just need a spring library on the classpath and little bit of configuration inside the application.yml

Currently this involves
- Spring Boot Actuator for Health Checks
- Micrometer Prometheus for Prometheus application metrics
- Opentelemetry for displaying Traces
- Logging, purely by leveraging SLF4J and logging to Stdout

### Swagger OpenAPI
As with monitoring, the example also exposes an OpenAPI Endpoint for API Documentation.
This can also be used by external rest clients or the fronted to generate client stubs.
It can be leveraged by just having the springdoc-openapi on the classpath.

### Resilience

Resilience ensures the stability of the System, even if an error occurs.

#### Low Coupling

For the Backend the overall architecture is designed in a way, that there is little to no coupling on between the backend containers, except for the Billing process.
A further mitigation is to use synchronization via a message broker, in specific case, again currently only as an idea for the billing.

#### Circuit breaker & Timeout

In cases where we need coupling via REST, the Circuit Breaker pattern will be leveraged.
This, together with a timeout ensures, that if there is a continuous failure in the communication, that the communication will be cut for a configured period of time.
The current configured Threshold is 10 seconds, which is already a long time.

#### Retry

It is also possible to implement a simple Retry mechanism if desirable.
Please note that for Retry the methods calls need to be idempotent.

#### Frontend Resilience

As the Frontend is ultimately coupled with all the Backends, via the Gateway, most of the Rest Calls need to be made resilient.
While this is theoretically possible inside the Gateway, we decided against it, to have more control.
The Frontend should at least have a timeout that cuts the connection after 10 seconds, usually it should respond in < 100ms.
Als Retry up to 5 times is possible. Please keep in mind that the Backend Methods need to be idempotent for this.

#### Validation

Validation can ensure that specific constraints like MinLen, MaxLen are kept for the Data Objects.
While it can be as simple as putting an annotation, there is a price tag to keep in mind.
As Validation might occur on the frontend + on the backend + inside the database, the constraints have to be kept in sync.
That means if for example, the database changes, everything must be updated.

So, when implementing Validation, follow these simple rules, that apply to the overall application:
Code should be readable and understandable
There should be as less code as possible
Validation should then at least occur inside the Controller Layer

That being said, the simplest kind of Validation is having Null Checks inside the DTOs.
For Java this can be just Object.requiresNull() .. in Kotlin thats already given.

The next possible Abstraction is, to have Validation annotations inside the DTOs (MinLen, MaxLen).
For more complex Usecases there is also the Pattern annotation that works with Regex.
This also requires Spring Boot Starter Validation Dependency, as well as putting a Valid Annotation on every Post Object inside the Controller.

What is highly discouraged is the usage of specific Validator Classes, for complex logic that contains multiple fields or Objects.
This often leads to Code that is much harder to understand, than just having the more honest approach,
having the code inside a simple “business validator class”, thats just manually called from the logic layer.

Testing the Validation

Validations should then be tested mostly with Unit tests.
There can be one simple Integration Test per class that the ensures the validation works.
But all other constraints (minlen, maxlen etc) should be tests with much faster unit tests.


## Build and Dependency Management
The build is covered by gradle, which is becoming more and more the successor to maven.
Dependency Management is mostly covered by Spring Boot.
id("io.spring.dependency-management") + combination of the Spring Boot Plugin will manage most of the dependencies.
So usually it is enough just to upgrade the Spring Boot Version.

There are a couple of dependencies that have to be handled additionally inside the constraints {} section.
Usage of further Dependencies outside what is managed here, should always have a cause and reason.
        
## Containers
### Docker Containers
The build process will also create a MultiArch JVM Container.
This is done by leveraging Google Jib, which needs no Docker installed and no Dockerfile.

This containers can then by run by a simple docker run command.
Or by a docker compose file inside src/deploy/docker.
The MultiArch Nature makes it possible for the images to run on Intel as well as Apple Silicon Machines.

### Native Images
During the build, also a Native Image will be created.
Build and Runtime of these Native image is also Architecture specific.
So to create an Intel and an Apple Silicon ARM Image, two build processes have to be executed,
on to different Processor Architectures.
This could be fused together afterwards to a multi arch image, or just left as two seperate images.

To keep the Application native image compatible, there are some considerations to be taken.
Dynamic Code like leveraging reflection should be avoided.
And also including a multitude of unverified 3rd party libraries.
This is also anyways a best practice, as the combination of these factor, higly decreases attack vectors.

### Frontend Combination
To combine the backend and frontend application(s) inside a container world there are at least two options.
The required result will always be a NPM package.
This NPM package could then be included inside the Microservice Tomcat,
during the buold with the "frontend-gradle-plugin".
This will essentially just do a npm install.

The alternative is two build one or multiple nginx containers, where the npm packages will be installed.

While the first option seems to be more "charming" inside the microservice world .. 
It artificially couples build and relase of the frontend and backend artifacts.
This also would make the most sense, if there always is a clear 1:1 relation between Frontend and Backend.
Which is often not .. as the Frontend even when leveraging Micro Frontends and multiple NPM Packages,
will always be a giant Single Page Application inside the browser.
That takes to a Giant Backend Application, where a Reverse Proxy Aggregates the Multiple Backends.
                                                                                                  
So it should be an "it dependends" decision.