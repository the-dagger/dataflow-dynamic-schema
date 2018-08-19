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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableDefinition;
import com.google.common.collect.Lists;
import java.util.Map;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.View;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for the {@link BigQuerySchemaMutator} class. */
@RunWith(JUnit4.class)
public class BigQuerySchemaMutatorTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  /** Tests the {@link BigQuerySchemaMutator} with failed inserts. */
  @Test
  public void testMutateSchema() {
    // Arrange
    //
    Schema schema =
        Schema.of(
            Field.of("id", LegacySQLTypeName.INTEGER), Field.of("name", LegacySQLTypeName.STRING));

    BigQuery bigQuery = mock(BigQuery.class);
    Table table = mock(Table.class);
    TableDefinition tableDef = mock(TableDefinition.class);

    when(bigQuery.getTable(any())).thenReturn(table);
    when(table.getDefinition()).thenReturn(tableDef);
    when(tableDef.getSchema()).thenReturn(schema);

    TableSchema tableSchema =
        new TableSchema()
            .setFields(
                Lists.newArrayList(
                    new TableFieldSchema().setName("id").setType("INTEGER"),
                    new TableFieldSchema().setName("name").setType("STRING"),
                    new TableFieldSchema().setName("age").setType("INTEGER")));

    TableRow tableRow = new TableRow().set("id", 1).set("name", "Ryan McDowell").set("age", 31);

    TableRowWithSchema tableRowWithSchema =
        TableRowWithSchema.newBuilder()
            .setTableName("test_table")
            .setTableRow(tableRow)
            .setTableSchema(tableSchema)
            .build();

    // Act
    //
    TableRowWithSchemaCoder.registerCoder(pipeline);

    PCollectionView<Map<Integer, TableRowWithSchema>> incomingRecordsView =
        pipeline
            .apply("CreateIncomingInput", Create.of(KV.of(tableRow.hashCode(), tableRowWithSchema)))
            .apply("CreateView", View.asMap());

    PCollection<TableRowWithSchema> result =
        pipeline
            .apply("CreateFailedInput", Create.<TableRow>of(tableRow))
            .apply(
                "MutateSchema",
                BigQuerySchemaMutator.mutateWithSchema(incomingRecordsView)
                    .withBigQueryService(bigQuery));

    // Assert
    //
    PAssert.that(result).containsInAnyOrder(tableRowWithSchema);

    pipeline.run();
  }
}
