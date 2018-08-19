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

package com.google.cloud.pso.bigquery;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.services.bigquery.model.TableRow;
import java.util.Map;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.PCollectionView;

/**
 *
 */
public class FailedInsertToTableRowWithSchema extends DoFn<TableRow, TableRowWithSchema> {

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
