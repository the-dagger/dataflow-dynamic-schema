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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import com.google.api.services.bigquery.model.TableCell;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import java.util.Arrays;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericRecord;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for the {@link BigQueryAvroUtils} class. */
@RunWith(JUnit4.class)
public class BigQueryAvroUtilsTest {

  private static Schema avroSchema;

  @BeforeClass
  public static void setup() {
    avroSchema =
        SchemaBuilder.record("testRecord")
            .namespace("test_table")
            .fields()
            .requiredInt("id")
            .requiredString("name")
            .requiredBoolean("isEngineer")
            .requiredBytes("photo")
            .requiredDouble("height")
            .requiredFloat("weight")
            .requiredLong("salary")
            .name("address")
            .type()
            .record("addressRecord")
            .fields()
            .requiredString("street")
            .requiredString("city")
            .endRecord()
            .noDefault()
            .endRecord();
  }

  @Test
  public void testGetTableSchema() {
    // Arrange
    //
    TableSchema expectedSchema =
        new TableSchema()
            .setFields(
                Arrays.asList(
                    new TableFieldSchema().setName("id").setType("INTEGER"),
                    new TableFieldSchema().setName("name").setType("STRING"),
                    new TableFieldSchema().setName("isEngineer").setType("BOOLEAN"),
                    new TableFieldSchema().setName("photo").setType("BYTES"),
                    new TableFieldSchema().setName("height").setType("NUMERIC"),
                    new TableFieldSchema().setName("weight").setType("FLOAT"),
                    new TableFieldSchema().setName("salary").setType("INTEGER"),
                    new TableFieldSchema()
                        .setName("address")
                        .setType("RECORD")
                        .setFields(
                            Arrays.asList(
                                new TableFieldSchema().setName("street").setType("STRING"),
                                new TableFieldSchema().setName("city").setType("STRING")))));

    // Act
    //
    TableSchema result = BigQueryAvroUtils.getTableSchema(avroSchema);

    // Assert
    //
    assertThat(result, is(equalTo(expectedSchema)));
  }

  @Test
  public void testGetTableRow() {
    // Arrange
    //
    Schema nestedRecordSchema =
        SchemaBuilder.record("addressRecord")
            .fields()
            .requiredString("street")
            .requiredString("city")
            .endRecord();

    GenericRecord nestedRecord = new Record(nestedRecordSchema);
    nestedRecord.put("street", "123 Fake St.");
    nestedRecord.put("city", "Austin, TX");

    GenericRecord record = new Record(avroSchema);
    record.put("id", 1);
    record.put("name", "Ryan McDowell");
    record.put("isEngineer", true);
    record.put("photo", new byte[] {42});
    record.put("height", 72.4);
    record.put("weight", 233.35);
    record.put("salary", 1L);
    record.put("address", nestedRecord);

    // Act
    //
    TableRow result = BigQueryAvroUtils.getTableRow(record);

    // Assert
    //
    assertThat(result.get("id"), is(equalTo(record.get("id"))));
    assertThat(result.get("name"), is(equalTo(record.get("name"))));
    assertThat(result.get("isEngineer"), is(equalTo(record.get("isEngineer"))));
    assertThat(result.get("photo"), is(equalTo(record.get("photo"))));
    assertThat(result.get("height"), is(equalTo(record.get("height"))));
    assertThat(result.get("weight"), is(equalTo(record.get("weight"))));
    assertThat(result.get("salary"), is(equalTo(record.get("salary"))));
    assertThat(
        ((TableCell) result.get("address")).get("street"), is(equalTo(nestedRecord.get("street"))));
    assertThat(
        ((TableCell) result.get("address")).get("city"), is(equalTo(nestedRecord.get("city"))));
  }
}
