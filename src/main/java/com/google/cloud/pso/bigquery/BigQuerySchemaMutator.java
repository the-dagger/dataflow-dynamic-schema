/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.pso.bigquery;

import com.google.api.services.bigquery.model.TableRow;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auto.value.AutoValue;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.Field.Mode;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;
import com.google.cloud.bigquery.TableId;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupIntoBatches;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.WithKeys;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;

/**
 * The {@link BigQuerySchemaMutator} class is a {@link PTransform} which given a PCollection of
 * TableRows, will compare the TableRows to an existing table schema in order to dynamically perform
 * schema updates on the output table.
 */
@AutoValue
public abstract class BigQuerySchemaMutator
    extends PTransform<PCollection<TableRow>, PCollection<TableRow>> {

  @Nullable
  abstract BigQuery getBigQueryService();

  abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder setBigQueryService(BigQuery bigQuery);
    abstract BigQuerySchemaMutator build();
  }

  public static BigQuerySchemaMutator of() {
    return new com.google.cloud.pso.bigquery.AutoValue_BigQuerySchemaMutator.Builder().build();
  }

  public BigQuerySchemaMutator withBigQueryService(BigQuery bigQuery) {
    return toBuilder().setBigQueryService(bigQuery).build();
  }

  @Override
  public PCollection<TableRow> expand(PCollection<TableRow> input) {

    // Here we'll key every failed record by the same key so we can batch the mutations being made
    // to BigQuery. The batch of records will then be passed to a schema mutator so the schema of
    // those records can be updated.
    input
        .apply("KeyRecords", WithKeys.of("failed-records"))
        .apply("GroupByKey", GroupIntoBatches.ofSize(1000L))
        .apply("RemoveKey", Values.create())
        .apply("MutateSchema", ParDo.of(new TableRowSchemaMutator(getBigQueryService())));

    return input;
  }

  /** */
  private class TableRowSchemaMutator extends DoFn<Iterable<TableRow>, TableRow> {

    private BigQuery bigQuery;

    public TableRowSchemaMutator() {}

    public TableRowSchemaMutator(BigQuery bigQuery) {
      this.bigQuery = bigQuery;
    }

    @Setup
    public void setup() throws IOException {
      if (bigQuery == null) {
        bigQuery =
            BigQueryOptions.newBuilder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
                .getService();
      }
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      Iterable<TableRow> mutatedRows = context.element();

      // Retrieve the table schema
      TableId tableId = TableId.of("data-analytics-pocs", "demo", "dynamic_schema");
      Table table = bigQuery.getTable(tableId);

      TableDefinition tableDef = table.getDefinition();
      Schema schema = tableDef.getSchema();

      // Compare the records to the known table schema
      Set<Field> additionalFields = getAdditionalFields(schema, mutatedRows);

      // Update the table schema for the new fields
      schema = addFieldsToSchema(schema, additionalFields);
      table
          .toBuilder()
          .setDefinition(tableDef.toBuilder().setSchema(schema).build())
          .build()
          .update();

      // Pass all rows downstream now that the schema of the output table has been mutated.
      mutatedRows.forEach(context::output);
    }

    /**
     * Retrieves the fields which have been added to the schema across all of the mutated rows.
     *
     * @param schema The schema to validate against.
     * @param mutatedRows The records which have mutated.
     * @return A unique set of fields which have been added to the schema.
     */
    private Set<Field> getAdditionalFields(Schema schema, Iterable<TableRow> mutatedRows) {

      // Compare the existingSchema to the mutated rows
      FieldList fieldList = schema.getFields();
      Set<Field> addedFields = Sets.newHashSet();

      mutatedRows.forEach(
          row ->
              row.keySet()
                  .stream()
                  .map(fieldName -> KV.of(fieldName, row.get(fieldName)))
                  .filter(field -> !isExistingField(fieldList, field.getKey()))
                  .map(
                      field ->
                          Field.of(field.getKey(), getLegacyFieldType(field.getValue()))
                              .toBuilder()
                              .setMode(Mode.NULLABLE)
                              .build())
                  .forEach(addedFields::add));

      return addedFields;
    }

    /**
     * Gets the {@link LegacySQLTypeName} for the object's type.
     *
     * @param obj The object to retrieve a type name for.
     * @return The {@link LegacySQLTypeName} which maps to the Java type of the object.
     */
    private LegacySQLTypeName getLegacyFieldType(Object obj) {
      if (obj instanceof String) {
        return LegacySQLTypeName.STRING;
      } else if (obj instanceof Integer) {
        return LegacySQLTypeName.INTEGER;
      } else if (obj instanceof Long) {
        return LegacySQLTypeName.INTEGER;
      } else if (obj instanceof BigDecimal) {
        return LegacySQLTypeName.NUMERIC;
      } else if (obj instanceof Double) {
        return LegacySQLTypeName.NUMERIC;
      } else if (obj instanceof Float) {
        return LegacySQLTypeName.FLOAT;
      } else if (obj instanceof Boolean) {
        return LegacySQLTypeName.BOOLEAN;
      } else if (obj instanceof Byte) {
        return LegacySQLTypeName.BYTES;
      } else if (obj instanceof Date) {
        return LegacySQLTypeName.DATETIME;
      }

      return LegacySQLTypeName.STRING;
    }

    /**
     * Checks whether the field name exists within the field list.
     *
     * @param fieldList The field list to validate the field against.
     * @param fieldName The field to check for.
     * @return True if the fieldName exists within the field list, false otherwise.
     */
    private boolean isExistingField(FieldList fieldList, String fieldName) {
      try {
        fieldList.get(fieldName);
        return true;
      } catch (IllegalArgumentException e) {
        return false;
      }
    }

    /**
     * Adds additional fields to an existing schema and returns a new schema containing all existing
     * and additional fields.
     *
     * @param schema The pre-existing schema to add fields to.
     * @param additionalFields The new fields to be added to the schema.
     * @return A new schema containing the existing and new fields added to the schema.
     */
    private Schema addFieldsToSchema(Schema schema, Set<Field> additionalFields) {
      List<Field> newFieldList = Lists.newArrayList();

      // Add the existing fields to the schema fields.
      newFieldList.addAll(schema.getFields());

      // Add all new fields to the schema fields.
      newFieldList.addAll(additionalFields);

      return Schema.of(newFieldList);
    }
  }
}
