# Use Spring Boot as Service Framework

* Status: decided
* Deciders: Andreas Mautsch
* Date: 2025-05-01

## Context

We need a (micro)service framework that is also compatible with Kubernetes
and allows for easy development and cloud native compatibility

## Decision
                                                              
The decision is to use Spring Boot, which is well known and adopted.
It features a rich eco system, a good core framework and also native image compatibility.


## Consequences

Spring Boot as to be adopted along all development teams.

## Possible Alternatives

A possible alternative would be Quarkus.
Which is similar to Spring Boot, but a relatively young framework.
It also puts the focus more on native images.