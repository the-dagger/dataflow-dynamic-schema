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

package com.google.cloud.pso.pipeline;

import com.google.cloud.pso.dofn.PubsubAvroToTableRowFn;
import com.google.cloud.pso.bigquery.BigQuerySchemaMutator;
import com.google.cloud.pso.bigquery.TableRowWithSchema;
import java.util.Map;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.bigquery.WriteResult;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.View;
import org.apache.beam.sdk.transforms.WithKeys;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.TupleTagList;
import org.joda.time.Duration;

/** TODO: Document. */
public class DynamicSchemaPipeline {

  /** TODO: Document. */
  public interface Options extends PipelineOptions {

    @Description("The Pub/Sub subscription to read messages from")
    @Required
    ValueProvider<String> getSubscription();

    void setSubscription(ValueProvider<String> value);

    @Description("The BigQuery table to write messages to")
    @Required
    ValueProvider<String> getTable();

    void setTable(ValueProvider<String> value);
  }

  /**
   * TODO: Document.
   *
   * @param args
   */
  public static void main(String[] args) {
    Options options = PipelineOptionsFactory.fromArgs(args).as(Options.class);

    run(options);
  }

  /**
   * TODO: Document.
   *
   * <p>TODO: Refactor so the pipeline structure can be tested without the sources / sinks
   *
   * @param options
   * @return
   */
  public static PipelineResult run(Options options) {

    // Create the pipeline
    Pipeline pipeline = Pipeline.create(options);

    // Execute pipeline and get the write result so we can react to the failed inserts
    PCollection<TableRowWithSchema> incomingRecords =
        pipeline
            .apply(
                "ReadAvroMessages",
                PubsubIO
                    .readMessagesWithAttributes()
                    .fromSubscription(options.getSubscription()))
            .apply("PubsubAvroToTableRowFn",
                ParDo
                    .of(new PubsubAvroToTableRowFn())
                    .withOutputTags(
                        PubsubAvroToTableRowFn.MAIN_OUT,
                        TupleTagList.of(PubsubAvroToTableRowFn.DEADLETTER_OUT)))
            .get(PubsubAvroToTableRowFn.MAIN_OUT)
            .apply("1mWindow", Window.into(FixedWindows.of(Duration.standardMinutes(1L))));


    WriteResult writeResult =
        incomingRecords.apply(
            "WriteToBigQuery",
            BigQueryIO.<TableRowWithSchema>write()
                .withFormatFunction(TableRowWithSchema::getTableRow)
                .withCreateDisposition(CreateDisposition.CREATE_NEVER)
                .withWriteDisposition(WriteDisposition.WRITE_APPEND)
                .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors()));

    // Create side-input to join records with their incoming schema
    PCollectionView<Map<Integer, TableRowWithSchema>> incomingRecordsView =
        incomingRecords
            .apply("KeyIncomingByHash", WithKeys.of(record -> record.getTableRow().hashCode()))
            .apply("CreateView", View.asMap());

    // Process the failed inserts by mutating the output table schema and retrying the insert
    writeResult
        .getFailedInserts()
        .apply("MutateSchema", BigQuerySchemaMutator.mutateWithSchema(incomingRecordsView))
        .apply(
            "RetryWriteMutatedRows",
            BigQueryIO.<TableRowWithSchema>write()
                .withFormatFunction(TableRowWithSchema::getTableRow)
                .withCreateDisposition(CreateDisposition.CREATE_NEVER)
                .withWriteDisposition(WriteDisposition.WRITE_APPEND));

    return pipeline.run();
  }
}
