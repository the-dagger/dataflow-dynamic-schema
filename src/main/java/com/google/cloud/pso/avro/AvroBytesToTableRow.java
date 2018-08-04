/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.pso.avro;

import com.google.api.services.bigquery.model.TableRow;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.beam.sdk.io.gcp.bigquery.TableRowJsonCoder;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.transforms.DoFn;

public class AvroBytesToTableRow extends DoFn<PubsubMessage, TableRow> {

  @ProcessElement
  public void processElement(ProcessContext context) {

    PubsubMessage message = context.element();

    try (SeekableByteArrayInput in = new SeekableByteArrayInput(message.getPayload())) {
      DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
      DataFileReader<GenericRecord> dataFileReader = new DataFileReader<>(in, datumReader);

      while (dataFileReader.hasNext()) {
        GenericRecord record = dataFileReader.next();
        context.output(convertGenericRecordToTableRow(record));
      }
    } catch (Exception e) {
      // Failed to decode record
    }
  }

  /**
   * TODO: This is a complete non-performant hack. Figure out a better way.
   *
   * @param record
   * @return
   */
  private static TableRow convertGenericRecordToTableRow(GenericRecord record)
      throws IOException {
    return TableRowJsonCoder.of().decode(new ByteArrayInputStream(record.toString().getBytes()));
  }
}
