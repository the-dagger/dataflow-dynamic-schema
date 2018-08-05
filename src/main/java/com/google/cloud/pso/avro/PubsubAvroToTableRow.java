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

package com.google.cloud.pso.avro;

import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.cloud.pso.bigquery.BigQueryAvroUtils;
import com.google.cloud.pso.bigquery.TableRowWithSchema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.TupleTag;

/**
 * A {@link DoFn} that converts a {@link PubsubMessage} with an Avro payload to a {@link
 * TableRowWithSchema} object.
 *
 * <p>The schema for the {@link TableRow} is inferred by inspecting and converting the Avro message
 * schema. By default, this function will set the namespace as the record's associated output table
 * for dynamic routing within the {@link org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO} sink using
 * {@link org.apache.beam.sdk.io.gcp.bigquery.DynamicDestinations}.
 */
public class PubsubAvroToTableRow extends DoFn<PubsubMessage, TableRowWithSchema> {

  public static final TupleTag<TableRowWithSchema> MAIN_OUT = new TupleTag<TableRowWithSchema>(){};
  public static final TupleTag<PubsubMessage> DEADLETTER_OUT = new TupleTag<PubsubMessage>(){};

  @ProcessElement
  void processElement(ProcessContext context) {

    PubsubMessage message = context.element();

    try (SeekableByteArrayInput in = new SeekableByteArrayInput(message.getPayload())) {
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
      DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(in, datumReader);

      while (dataFileReader.hasNext()) {
        GenericRecord record = dataFileReader.next();

        String tableName = record.getSchema().getNamespace();
        TableSchema tableSchema = BigQueryAvroUtils.getTableSchema(record.getSchema());
        TableRow tableRow = BigQueryAvroUtils.getTableRow(record);

        context.output(
            MAIN_OUT,
            TableRowWithSchema.newBuilder()
                .setTableName(tableName)
                .setTableSchema(tableSchema)
                .setTableRow(tableRow)
                .build());
      }
    } catch (Exception e) {
      // Redirect all failed records to the dead-letter.
      context.output(DEADLETTER_OUT, message);
    }
  }
}
