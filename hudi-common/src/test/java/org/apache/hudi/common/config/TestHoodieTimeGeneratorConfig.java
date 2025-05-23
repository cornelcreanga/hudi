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

package org.apache.hudi.common.config;

import org.apache.hudi.common.util.StringUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.apache.hudi.common.config.HoodieCommonConfig.BASE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestHoodieTimeGeneratorConfig {

  @ParameterizedTest
  @CsvSource(value = {
      "org.apache.hudi.client.transaction.lock.InProcessLockProvider,1",
      "org.apache.hudi.client.transaction.lock.ZookeeperBasedLockProvider,200",
      ",1",
      "any_string,200"
  })
  void testMaxSkewDefaults(String lockProvider, long expected) {
    TypedProperties properties = new TypedProperties();
    properties.setProperty(BASE_PATH.key(), "/tmp/path");
    if (!StringUtils.isNullOrEmpty(lockProvider)) {
      properties.setProperty(HoodieTimeGeneratorConfig.LOCK_PROVIDER_KEY, lockProvider);
    }
    HoodieTimeGeneratorConfig config = HoodieTimeGeneratorConfig.newBuilder().fromProperties(properties).build();
    assertEquals(expected, config.getMaxExpectedClockSkewMs());
  }
}
