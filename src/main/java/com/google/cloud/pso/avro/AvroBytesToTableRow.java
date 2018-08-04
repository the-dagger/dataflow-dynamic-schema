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
