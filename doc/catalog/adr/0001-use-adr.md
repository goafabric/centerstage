# Use ADR for documenting Architecture Decissions

* Status: proposed
* Deciders: Andreas Mautsch
* Date: 2025-03-01

## Context

At it's best currently technical concepts are written,
following a functional concept from product side.

This however often happens only once at the beginning of the project.
Or for smaller projects never at all.
Also when change requests are processed later on,
the concept usually is not getting updated to reflect that.

That already had multiple times negative consequences on the overall architecture.
Especially when including external systems / dependencies.

## Decision

The decision is to use Architecture Decision Records like this one.
They are lightweight in nature and should be simple and short.
This is at least mandatory when adding external systems / dependencies,
after the initial technical concept.

Examples are
- Adding new services via REST
- Adding new kafka producers or consumers
- Adding new databases like Postgres, S3, Elasticsearch ...
- Decisions that affect Multi Tenancy (e.g. not tenant specific storage)

For now if a change like this happens,
an ADR should be created inside the GIT Repository, via a Merge Request.
And can then be reviewed (by an architect).

## Consequences

At it's best this will help us to make architecture decisions more transparent.
And also to get an architecture log over time, to find what decision was done when and why.
This is however extra work for developers and architects.

## Possible Alternatives

We continue without ADR and live with surprising architecture decisions,
now often found when going into production.