# Use Spring Batch for Batch Process

* Status: proposed
* Deciders: Andreas Mautsch
* Date: 2025-05-10

## Context

For Batch Processes, like importing a bulk of catalog data,
we need a batch framework.
To allow for efficient and easy importing of the data

## Decision
          
The decision is to use Spring Batch, which is well known and widely adopted.                                                    
It is easy to learn, and yet up to more complex tasks.
It is also optimized for performance and processing in parallel.
As well as operational features, like protocol tables


## Consequences

Spring Batch as to be adopted along all development teams.

## Possible Alternatives

A possible alternative would be Jobrunr.
But this system is designed to be more like a simple scheduler,
and less a complete batch process.