# Dataflow Dynamic Schema

Commonly when consuming data from 3rd parties, you may find yourself subject to having to react to
upstream schema changes. A rudimentary way to handle this scenario may be to coordinate releases 
with the partner or sink records which have mutated schema into a dead-letter table. However, it is
also possible to handle this scenario dynamically in real-time within the pipeline itself. This
repository contains an example pipeline which, on failed inserts to BigQuery, will mutate the output
table with any additive changes on the incoming record. This frees you up from the operational
overhead of having to manage these changes and keeps your schema up-to-date in the face of constant
change.

This pipeline is largely based on the
[How to handle mutating JSON schemas in a streaming pipeline, with Square Enix](https://cloud.google.com/blog/products/gcp/how-to-handle-mutating-json-schemas-in-a-streaming-pipeline-with-square-enix)
blog post.

![alt text](https://storage.googleapis.com/gweb-cloudblog-publish/images/mutating-json-2a2es.max-900x900.PNG "Pipeline Architecture")

## Pipeline

[DynamicSchemaPipeline](src/main/java/com/google/cloud/pso/pipeline/DynamicSchemaPipeline.java) -
A pipeline which consumes Avro messages from Pub/Sub and outputs records to BigQuery. If the Avro
contains additional fields, the output BigQuery table will be mutated to automatically add the 
changes.

## Getting Started

### Requirements

* Java 8
* Maven 3

### Building the Project

Build the entire project using the maven compile command.
```sh
mvn clean && mvn compile
```
