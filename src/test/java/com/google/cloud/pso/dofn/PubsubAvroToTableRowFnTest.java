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

package com.google.cloud.pso.dofn;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.pso.bigquery.TableRowWithSchema;
import com.google.cloud.pso.bigquery.TableRowWithSchemaCoder;
import com.google.common.collect.Maps;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.testing.NeedsRunner;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTagList;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link PubsubAvroToTableRowFn}. */
@RunWith(JUnit4.class)
public class PubsubAvroToTableRowFnTest {

  @Rule public final transient TestPipeline pipeline = TestPipeline.create();

  private static Schema avroSchema;
  private static Record avroRecord;
  private static TableRowWithSchema expectedTableRow;

  @BeforeClass
  public static void setup() {
    avroSchema =
        SchemaBuilder.record("testRecord")
            .namespace("test_table")
            .fields()
            .requiredInt("id")
            .requiredString("name")
            .endRecord();

    avroRecord = new Record(avroSchema);
    avroRecord.put("id", 1);
    avroRecord.put("name", "Ryan McDowell");

    expectedTableRow =
        TableRowWithSchema.newBuilder()
            .setTableName("test_table")
            .setTableSchema(
                new TableSchema()
                    .setFields(
                        Arrays.asList(
                            new TableFieldSchema().setName("id").setType("INTEGER"),
                            new TableFieldSchema().setName("name").setType("STRING"))))
            .setTableRow(new TableRow().set("id", 1).set("name", "Ryan McDowell"))
            .build();
  }

  /**
   * Tests the {@link PubsubAvroToTableRowFn#processElement} method successfully transforms Avro
   * payloads to {@link com.google.cloud.pso.bigquery.TableRowWithSchema} objects.
   */
  @Test
  @Category(NeedsRunner.class)
  public void testProcessElementGoodInput() throws IOException {
    // Arrange
    //
    Map<String, String> attributes = Maps.newHashMap();
    byte[] payload = encodeRecordToBytes(avroSchema, avroRecord);

    PubsubMessage message = new PubsubMessage(payload, attributes);

    // Act
    //
    TableRowWithSchemaCoder.registerCoder(pipeline);

    PCollectionTuple result =
        pipeline
            .apply("CreateInput", Create.of(message))
            .apply(
                "AvroToTableRow",
                ParDo.of(new PubsubAvroToTableRowFn())
                    .withOutputTags(
                        PubsubAvroToTableRowFn.MAIN_OUT,
                        TupleTagList.of(PubsubAvroToTableRowFn.DEADLETTER_OUT)));

    // Assert
    //
    PAssert.that(result.get(PubsubAvroToTableRowFn.MAIN_OUT)).containsInAnyOrder(expectedTableRow);

    PAssert.that(result.get(PubsubAvroToTableRowFn.DEADLETTER_OUT)).empty();

    pipeline.run();
  }

  /**
   * Tests the {@link PubsubAvroToTableRowFn#processElement} method when the input is malformed and
   * cannot be processed.
   */
  @Test
  @Category(NeedsRunner.class)
  public void testProcessElementMalformedInput() {
    // Arrange
    //
    final Map<String, String> attributes = Maps.newHashMap();
    final byte[] payload = new byte[] {42};

    PubsubMessage message = new PubsubMessage(payload, attributes);

    // Act
    //
    TableRowWithSchemaCoder.registerCoder(pipeline);

    PCollectionTuple result =
        pipeline
            .apply("CreateInput", Create.of(message))
            .apply(
                "AvroToTableRow",
                ParDo.of(new PubsubAvroToTableRowFn())
                    .withOutputTags(
                        PubsubAvroToTableRowFn.MAIN_OUT,
                        TupleTagList.of(PubsubAvroToTableRowFn.DEADLETTER_OUT)));

    // Assert
    //
    PAssert.that(result.get(PubsubAvroToTableRowFn.MAIN_OUT)).empty();

    PAssert.that(result.get(PubsubAvroToTableRowFn.DEADLETTER_OUT))
        .satisfies(
            collection -> {
              PubsubMessage errorMsg = collection.iterator().next();
              assertThat(errorMsg.getAttributeMap(), is(equalTo(attributes)));
              assertThat(errorMsg.getPayload(), is(equalTo(payload)));
              // Return null as expected else an exception is thrown.
              return null;
            });

    pipeline.run();
  }

  /**
   * Encodes a {@link GenericRecord} to a byte array.
   *
   * @param record The record to encode.
   * @return The encoded record as a byte array.
   */
  private byte[] encodeRecordToBytes(Schema schema, Record record) throws IOException {
    byte[] encodedRecord;

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      try (DataFileWriter<GenericRecord> dataFileWriter =
          new DataFileWriter<>(new GenericDatumWriter<>(schema))) {

        dataFileWriter.create(schema, out).append(record);
      }

      encodedRecord = out.toByteArray();
    }

    return encodedRecord;
  }
}
