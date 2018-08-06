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

import com.google.api.services.bigquery.model.TableRow;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.common.collect.Lists;
import java.util.List;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for the {@link BigQuerySchemaMutator} class.
 */
@RunWith(JUnit4.class)
public class BigQuerySchemaMutatorTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  @Test
  public void testDummy() {}

  //@Test
  public void test() {
    // Create mock services
    Schema schema =
        Schema.of(
            Field.of("id", LegacySQLTypeName.INTEGER), Field.of("name", LegacySQLTypeName.STRING));

//    BigQuery bigQuery = mock(BigQuery.class);
//    Table table = mock(Table.class);
//    TableDefinition tableDef = mock(TableDefinition.class);
//
//    when(bigQuery.getTable(any())).thenReturn(table);
//    when(table.getDefinition()).thenReturn(tableDef);
//    when(tableDef.getSchema()).thenReturn(schema);

    // Create test input
    List<TableRow> mutatedRows = Lists.newArrayList();
    mutatedRows.add(
        new TableRow()
            .set("id", 1)
            .set("name", "Ryan"));
    mutatedRows.add(
        new TableRow()
            .set("id", 2)
            .set("name", "Samantha")
            .set("age", 29));
    mutatedRows.add(
        new TableRow()
            .set("id", 3)
            .set("name", "Aubrey")
            .set("age", 10)
            .set("isEngineer", false));

    // Execute the schema mutator
    PCollection<TableRow> result =
        pipeline
            .apply("CreateInput", Create.of(mutatedRows));

    // Test the result
    PAssert
        .that(result)
        .containsInAnyOrder(mutatedRows);

    // Run the pipeline
    pipeline.run();
  }
}
