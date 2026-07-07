# Use Kafka for Event Sourcing

* Status: decided
* Deciders: Andreas Mautsch
* Date: 2025-05-10

## Context

We need a system that can provide data for event sourcing.
Which is basically replication of the data to other internal or external systems.

## Decision

The decision is to use Kafka.
Kafka is a mature system, that also features excellent support for spring boot.
As well as performance.

## Consequences

Systems need to be capable of working with eventually consistency.
At a deployment level, this also means maintaining an additional technology.

## Possible Alternatives

Possible alternatives could be NATS or RabbitMQ.
We evaluated NATS which claims to be a more lightweight alternative.
But it proved to be inferior concerning Spring Boot Integration, as well as performance.

RabbitMQ on the other hand, is more of a traditional Message Broker.
Not designed to push larges blobs of data.

A possible alternative would be Quarkus.
Which is similar to Spring Boot, but a relatively young framework.
It also puts the focus more on native images.