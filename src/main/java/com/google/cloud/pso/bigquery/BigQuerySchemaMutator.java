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

import com.google.api.services.bigquery.model.TableRow;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import java.io.IOException;
import org.apache.beam.sdk.transforms.DoFn;

public class BigQuerySchemaMutator extends DoFn<TableRow, TableRow> {

  private BigQuery bigQuery;

  @Setup
  public void setup() throws IOException {
    bigQuery =
        BigQueryOptions.newBuilder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()
            .getService();
  }

  @ProcessElement
  public void processElement(ProcessContext context) {
    // TODO
  }
}
