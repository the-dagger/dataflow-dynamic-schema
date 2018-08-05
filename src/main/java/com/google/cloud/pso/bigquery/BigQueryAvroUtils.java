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

import com.google.api.client.json.GenericJson;
import com.google.api.services.bigquery.model.TableCell;
import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.apache.avro.generic.GenericRecord;

/**
 * The {@link BigQueryAvroUtils} class provides utilities for converting records from Avro to {@link
 * TableRow} objects for insertion into BigQuery.
 */
public class BigQueryAvroUtils {

  /**
   * Converts an Avro schema into a BigQuery {@link TableSchema} object.
   *
   * @param schema The Avro schema to convert.
   * @return The equivalent schema as a {@link TableSchema} object.
   */
  public static TableSchema getTableSchema(Schema schema) {
    return new TableSchema().setFields(getFieldsSchema(schema.getFields()));
  }

  /**
   * Converts an Avro {@link GenericRecord} into a BigQuery {@link TableRow} object. NOTE: At this
   * time Arrays and logical Avro types are not supported for conversion.
   *
   * @param record The Avro record to convert.
   * @return The equivalent record as a {@link TableRow} object.
   */
  public static TableRow getTableRow(GenericRecord record) {
    TableRow row = new TableRow();
    encode(record, row);
    return row;
  }

  /**
   * Converts an Avro {@link GenericRecord} into a BigQuery {@link TableCell} record object.
   * @param record The record to convert.
   * @return The equivalent record as a {@link TableCell} object.
   */
  public static TableCell getTableCell(GenericRecord record) {
    TableCell cell = new TableCell();
    encode(record, cell);
    return cell;
  }

  /**
   * Encodes a {@link GenericRecord} as {@link TableRow} object. NOTE: At this * time Arrays and
   * logical Avro types are not supported for conversion.
   *
   * @param record The Avro record to encode.
   * @param row The {@link TableRow} object which will be populated with the encoded record.
   */
  private static void encode(GenericRecord record, GenericJson row) {
    Schema schema = record.getSchema();
    schema
        .getFields()
        .forEach(
            field -> {
              Type type = field.schema().getType();
              switch (type) {
                case RECORD:
                  row.set(field.name(), getTableCell((GenericRecord) record.get(field.pos())));
                  break;
                case INT:
                case LONG:
                  row.set(field.name(), ((Number) record.get(field.pos())).longValue());
                  break;
                case FLOAT:
                case DOUBLE:
                  row.set(field.name(), ((Number) record.get(field.pos())).doubleValue());
                  break;
                case BOOLEAN:
                  row.set(field.name(), record.get(field.pos()));
                  break;
                default:
                  row.set(field.name(), String.valueOf(record.get(field.pos())));
              }
            });
  }

  /**
   * Converts a list of Avro fields to a list of BigQuery {@link TableFieldSchema} objects.
   * @param fields The Avro fields to convert to a BigQuery schema.
   * @return The equivalent fields which can be used to populate a BigQuery schema.
   */
  private static List<TableFieldSchema> getFieldsSchema(List<Schema.Field> fields) {
    return fields
        .stream()
        .map(
            field -> {
              TableFieldSchema column = new TableFieldSchema().setName(field.name());
              Type type = field.schema().getType();
              switch (type) {
                case RECORD:
                  column.setType("RECORD");
                  column.setFields(getFieldsSchema(fields));
                  break;
                case INT:
                case LONG:
                  column.setType("INTEGER");
                  break;
                case BOOLEAN:
                  column.setType("BOOLEAN");
                  break;
                case FLOAT:
                case DOUBLE:
                  column.setType("FLOAT");
                  break;
                default:
                  column.setType("STRING");
              }
              return column;
            })
        .collect(Collectors.toList());
  }
}
