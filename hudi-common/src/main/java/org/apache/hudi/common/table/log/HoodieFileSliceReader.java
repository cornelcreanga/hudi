/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hudi.common.table.log;

import org.apache.hudi.common.config.TypedProperties;
import org.apache.hudi.common.model.HoodiePayloadProps;
import org.apache.hudi.common.model.HoodieRecord;
import org.apache.hudi.common.model.HoodieRecordMerger;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.common.util.collection.ClosableIterator;
import org.apache.hudi.common.util.collection.Pair;
import org.apache.hudi.exception.HoodieIOException;
import org.apache.hudi.io.storage.HoodieFileReader;
import org.apache.hudi.keygen.BaseKeyGenerator;

import org.apache.avro.Schema;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class HoodieFileSliceReader<T> extends LogFileIterator<T> {
  private final Option<HoodieFileReader> baseFileReader;
  private final Option<ClosableIterator<HoodieRecord>> baseFileIterator;
  private final Schema schema;
  private final Properties props;

  private final TypedProperties payloadProps = new TypedProperties();
  private final Option<Pair<String, String>> simpleKeyGenFieldsOpt;
  private final Option<BaseKeyGenerator> keyGeneratorOpt;
  Map<String, HoodieRecord> records;
  HoodieRecordMerger merger;

  public HoodieFileSliceReader(Option<HoodieFileReader> baseFileReader,
                               HoodieMergedLogRecordScanner scanner, Schema schema, String preCombineField, HoodieRecordMerger merger,
                               Properties props, Option<Pair<String, String>> simpleKeyGenFieldsOpt, Option<BaseKeyGenerator> keyGeneratorOpt) throws IOException {
    super(scanner);
    this.baseFileReader = baseFileReader;
    if (baseFileReader.isPresent()) {
      this.baseFileIterator = Option.of(baseFileReader.get().getRecordIterator(schema));
    } else {
      this.baseFileIterator = Option.empty();
    }
    this.schema = schema;
    this.merger = merger;
    if (preCombineField != null) {
      payloadProps.setProperty(HoodiePayloadProps.PAYLOAD_ORDERING_FIELD_PROP_KEY, preCombineField);
    }
    this.props = props;
    this.simpleKeyGenFieldsOpt = simpleKeyGenFieldsOpt;
    this.keyGeneratorOpt = keyGeneratorOpt;
    this.records = this.scanner.getRecords();
  }

  private boolean hasNextInternal() {
    while (baseFileIterator.isPresent() && baseFileIterator.get().hasNext()) {
      try {
        HoodieRecord currentRecord = baseFileIterator.get().next();
        String recordKey = currentRecord.getRecordKey(schema, keyGeneratorOpt);
        Option<HoodieRecord> logRecord = removeLogRecord(recordKey);
        if (!logRecord.isPresent()) {
          nextRecord = currentRecord.wrapIntoHoodieRecordPayloadWithParams(schema, props, simpleKeyGenFieldsOpt, scanner.isWithOperationField(),
              scanner.getPartitionNameOverride(), false, Option.empty());
          return true;
        }
        Option<Pair<HoodieRecord, Schema>> mergedRecordOpt = merger.merge(currentRecord, schema, logRecord.get(), schema, payloadProps);
        if (mergedRecordOpt.isPresent()) {
          HoodieRecord<T> mergedRecord = (HoodieRecord<T>) mergedRecordOpt.get().getLeft();
          nextRecord = mergedRecord.wrapIntoHoodieRecordPayloadWithParams(schema, props, simpleKeyGenFieldsOpt, scanner.isWithOperationField(),
              scanner.getPartitionNameOverride(), false, Option.empty());
          return true;
        }
      } catch (IOException e) {
        throw new HoodieIOException("Failed to wrapIntoHoodieRecordPayloadWithParams: " + e.getMessage());
      }
    }
    return super.doHasNext();
  }

  @Override
  protected boolean doHasNext() {
    return hasNextInternal();
  }

  @Override
  public void close() {
    super.close();
    if (baseFileIterator.isPresent()) {
      baseFileIterator.get().close();
    }
    if (baseFileReader.isPresent()) {
      baseFileReader.get().close();
    }
  }
}
