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
import com.google.api.services.bigquery.model.TableSchema;
import com.google.auto.value.AutoValue;

/**
 * The {@link TableRowWithSchema} class provides a wrapper around a {@link TableRow} to maintain
 * additional metadata information about the table to which the row belongs and that table's schema.
 */
@AutoValue
public abstract class TableRowWithSchema {

  public abstract String getTableName();

  public abstract TableSchema getTableSchema();

  public abstract TableRow getTableRow();

  public abstract Builder toBuilder();

  public static Builder newBuilder() {
    return new com.google.cloud.pso.bigquery.AutoValue_TableRowWithSchema.Builder();
  }

  /**
   * The builder for the {@link TableRowWithSchema} class.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTableName(String tableName);

    public abstract Builder setTableSchema(TableSchema tableSchema);

    public abstract Builder setTableRow(TableRow tableRow);

    public abstract TableRowWithSchema build();
  }
}
