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

package com.google.cloud.pso.dofn;

import com.google.cloud.pso.bigquery.TableRowWithSchema;
import com.google.common.collect.Maps;
import java.util.Map;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link PubsubAvroToTableRowFn}. */
@RunWith(JUnit4.class)
public class PubsubAvroToTableRowFnTest {

  @Rule
  public final transient TestPipeline pipeline = TestPipeline.create();

  /**
   * Tests the {@link PubsubAvroToTableRowFn#processElement} method successfully transforms Avro
   * payloads to {@link com.google.cloud.pso.bigquery.TableRowWithSchema} objects.
   */
  @Test
  public void testProcessElement() {

    // Arrange
    //
    Map<String, String> attributes = Maps.newHashMap();
    byte[] payload = null;

    PubsubMessage message = new PubsubMessage(payload, attributes);

    TableRowWithSchema expected = TableRowWithSchema.newBuilder()
        .setTableName("")
        .setTableRow(null)
        .setTableSchema(null)
        .build();

    // Act
    //
    PCollection<TableRowWithSchema> tableRows = pipeline
        .apply("CreateInput", Create.of(message))
        .apply("AvroToTableRow", ParDo.of(new PubsubAvroToTableRowFn()));

    // Assert
    //
    PAssert.that(tableRows).containsInAnyOrder(expected);

    pipeline.run();
  }
}
