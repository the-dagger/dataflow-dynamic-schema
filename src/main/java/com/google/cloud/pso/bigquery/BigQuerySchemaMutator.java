/*
 * Copyright (C) 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pso.bigquery;

import static com.google.common.base.Preconditions.checkNotNull;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.GroupIntoBatches;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.transforms.WithKeys;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;

/**
 * The {@link BigQuerySchemaMutator} class is a {@link PTransform} which given a PCollection of
 * TableRows, will compare the TableRows to an existing table schema in order to dynamically perform
 * schema updates on the output table.
 */
@AutoValue
public abstract class BigQuerySchemaMutator
    extends PTransform<PCollection<TableRow>, PCollection<TableRowWithSchema>> {

  @Nullable
  abstract BigQuery getBigQueryService();

  abstract PCollectionView<Map<Integer, TableRowWithSchema>> getIncomingRecordsView();

  abstract Builder toBuilder();

  /** The builder for the {@link BigQuerySchemaMutator} class. */
  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder setBigQueryService(BigQuery bigQuery);

    abstract Builder setIncomingRecordsView(
        PCollectionView<Map<Integer, TableRowWithSchema>> incomingRecordsView);

    abstract BigQuerySchemaMutator build();
  }

  public static BigQuerySchemaMutator mutateWithSchema(
      PCollectionView<Map<Integer, TableRowWithSchema>> incomingRecordsView) {
    return new com.google.cloud.pso.bigquery.AutoValue_BigQuerySchemaMutator.Builder()
        .setIncomingRecordsView(incomingRecordsView)
        .build();
  }

  /**
   * @param bigQuery
   * @return
   */
  public BigQuerySchemaMutator withBigQueryService(BigQuery bigQuery) {
    return toBuilder().setBigQueryService(bigQuery).build();
  }

  @Override
  public PCollection<TableRowWithSchema> expand(PCollection<TableRow> input) {

    // Here we'll key every failed record by the same key so we can batch the mutations being made
    // to BigQuery. The batch of records will then be passed to a schema mutator so the schema of
    // those records can be updated.
    PCollection<TableRowWithSchema> mutatedRecords =
        input
            .apply(
                "FailedInsertToTableRowWithSchema",
                ParDo.of(new FailedInsertToTableRowWithSchema(getIncomingRecordsView()))
                    .withSideInputs(getIncomingRecordsView()))
            .apply("KeyRecords", WithKeys.of("failed-record-batch"))
            .apply("GroupInBatches", GroupIntoBatches.ofSize(1000L))
            .apply("RemoveKey", Values.create())
            .apply("MutateSchema", ParDo.of(new TableRowSchemaMutator()));

    return mutatedRecords;
  }

  /** */
  public static class FailedInsertToTableRowWithSchema extends DoFn<TableRow, TableRowWithSchema> {

    private PCollectionView<Map<Integer, TableRowWithSchema>> incomingRecordsView;

    public FailedInsertToTableRowWithSchema(
        PCollectionView<Map<Integer, TableRowWithSchema>> incomingRecordsView) {
      this.incomingRecordsView = incomingRecordsView;
    }

    @ProcessElement
    public void processElement(ProcessContext context) {
      TableRow failedInsert = context.element();

      Map<Integer, TableRowWithSchema> schemaMap = context.sideInput(incomingRecordsView);
      TableRowWithSchema tableRowWithSchema = schemaMap.get(failedInsert.hashCode());

      checkNotNull(tableRowWithSchema, "Unable to retrieve schema for failed insert.");

      context.output(tableRowWithSchema);
    }
  }

  /** */
  public static class TableRowSchemaMutator
      extends DoFn<Iterable<TableRowWithSchema>, TableRowWithSchema> {

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
      Iterable<TableRowWithSchema> mutatedRows = context.element();

      // Retrieve the table schema
      TableId tableId = TableId.of("data-analytics-pocs", "demo", "dynamic_schema");
      Table table = bigQuery.getTable(tableId);

      checkNotNull(table, "Failed to find table to mutate: " + tableId.toString());

      TableDefinition tableDef = table.getDefinition();
      Schema schema = tableDef.getSchema();

      checkNotNull(table, "Unable to retrieve schema for table: " + tableId.toString());

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
    private Set<Field> getAdditionalFields(
        Schema schema, Iterable<TableRowWithSchema> mutatedRows) {

      // Compare the existingSchema to the mutated rows
      FieldList fieldList = schema.getFields();
      Set<Field> additionalFields = Sets.newHashSet();

      mutatedRows.forEach(
          row ->
              row.getTableSchema()
                  .getFields()
                  .stream()
                  .filter(field -> !isExistingField(fieldList, field.getName()))
                  .map(
                      field ->
                          Field.of(field.getName(), LegacySQLTypeName.valueOf(field.getType()))
                              .toBuilder()
                              .setMode(Mode.NULLABLE)
                              .build())
                  .forEach(additionalFields::add));

      return additionalFields;
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
