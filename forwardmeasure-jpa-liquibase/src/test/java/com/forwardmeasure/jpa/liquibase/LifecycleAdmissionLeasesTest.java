/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.forwardmeasure.jpa.liquibase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Real control-plane PostgreSQL coverage; no application-local semaphore or clock assumptions. */
@WithPostgreSqlContainer(databaseName = "lifecycle_admission_contract")
class LifecycleAdmissionLeasesTest {
  @Test
  void expiredClaimCannotRegisterOrReleaseItsReplacement(PostgreSqlTestContainer database)
      throws Exception {
    new TenantRegistry(database.dataSource()).migrate();
    var leases = new LifecycleAdmissionLeases(database.dataSource());
    UUID instance = UUID.randomUUID();
    leases.provision(new LifecycleAdmissionLeases.Capacity(instance, 1, 200));
    assertEquals(1, leases.capacity(instance).orElseThrow().slots());
    var stale =
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .orElseThrow();
    assertTrue(
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .isEmpty());
    try (var connection = database.dataSource().getConnection();
        var expire =
            connection.prepareStatement(
                "UPDATE lifecycle_admission_lease SET claim_deadline = clock_timestamp() - interval"
                    + " '1 second' WHERE instance_id = ?")) {
      expire.setObject(1, instance);
      assertEquals(1, expire.executeUpdate());
    }
    var replacement =
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .orElseThrow();
    assertFalse(leases.register(stale, Duration.ofSeconds(120), Duration.ofSeconds(10)));
    assertFalse(leases.release(stale));
    assertTrue(leases.register(replacement, Duration.ofSeconds(120), Duration.ofSeconds(10)));
    // Registration, not the old claim deadline, controls recovery once a transaction can hold
    // guards.
    try (var connection = database.dataSource().getConnection();
        var expire =
            connection.prepareStatement(
                "UPDATE lifecycle_admission_lease SET claim_deadline = clock_timestamp() - interval"
                    + " '1 second' WHERE instance_id = ?")) {
      expire.setObject(1, instance);
      expire.executeUpdate();
    }
    assertTrue(
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .isEmpty());
    assertTrue(leases.release(replacement));
    assertFalse(leases.release(replacement));
    assertTrue(
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .isPresent());
  }

  @Test
  void provisionedCapacityCannotDriftAcrossServices(PostgreSqlTestContainer database) {
    new TenantRegistry(database.dataSource()).migrate();
    var leases = new LifecycleAdmissionLeases(database.dataSource());
    UUID instance = UUID.randomUUID();
    var capacity = new LifecycleAdmissionLeases.Capacity(instance, 2, 200);
    leases.provision(capacity);
    leases.provision(capacity);
    assertEquals(capacity, leases.capacity(instance).orElseThrow());
    assertThrows(
        IllegalStateException.class,
        () -> leases.provision(new LifecycleAdmissionLeases.Capacity(instance, 1, 200)));
    assertThrows(
        IllegalStateException.class,
        () -> leases.provision(new LifecycleAdmissionLeases.Capacity(instance, 2, 300)));
    var first =
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .orElseThrow();
    var second =
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .orElseThrow();
    assertTrue(
        leases
            .claim(instance, UUID.randomUUID(), UUID.randomUUID(), Duration.ofSeconds(30))
            .isEmpty());
    assertTrue(leases.release(first));
    assertTrue(leases.release(second));
  }
}
