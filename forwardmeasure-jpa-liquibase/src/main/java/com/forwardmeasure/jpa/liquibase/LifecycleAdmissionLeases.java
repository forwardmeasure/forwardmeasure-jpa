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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Instance-wide transaction admission slots in the platform database. The supplied datasource must
 * be a dedicated, non-enlisted pool: every statement here commits independently of the caller's
 * tenant transaction. Slot reclamation is tied to that transaction's server deadline.
 */
public final class LifecycleAdmissionLeases {
  private final DataSource controlPlane;

  public record Capacity(UUID instanceId, int slots, int connectionBudget) {
    public Capacity {
      Objects.requireNonNull(instanceId, "instanceId");
      if (slots < 1 || connectionBudget < 1)
        throw new IllegalArgumentException("Invalid admission capacity");
    }
  }

  public record Claim(UUID instanceId, int slot, UUID holderToken) {}

  public LifecycleAdmissionLeases(DataSource controlPlane) {
    this.controlPlane = Objects.requireNonNull(controlPlane, "controlPlane");
  }

  /** Provisioning only. Existing instance capacity can change only with writers stopped. */
  public void provision(Capacity capacity) {
    try (Connection connection = controlPlane.getConnection()) {
      requireIndependent(connection);
      connection.setAutoCommit(false);
      try {
        try (PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO lifecycle_admission_instance(instance_id,connection_budget) VALUES"
                    + " (?,?) ON CONFLICT (instance_id) DO NOTHING")) {
          statement.setObject(1, capacity.instanceId());
          statement.setInt(2, capacity.connectionBudget());
          statement.executeUpdate();
        }
        try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT connection_budget FROM lifecycle_admission_instance WHERE instance_id = ?"
                    + " FOR UPDATE")) {
          statement.setObject(1, capacity.instanceId());
          try (var rows = statement.executeQuery()) {
            if (!rows.next() || rows.getInt(1) != capacity.connectionBudget())
              throw new IllegalStateException(
                  "Instance connection budget differs from provisioned capacity; stop writers"
                      + " before changing it");
          }
        }
        int slots;
        try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT count(*) FROM lifecycle_admission_lease WHERE instance_id = ?")) {
          statement.setObject(1, capacity.instanceId());
          try (var rows = statement.executeQuery()) {
            rows.next();
            slots = rows.getInt(1);
          }
        }
        if (slots == 0) {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO lifecycle_admission_lease(instance_id,slot,state) VALUES"
                      + " (?,?,'free')")) {
            for (int slot = 0; slot < capacity.slots(); slot++) {
              statement.setObject(1, capacity.instanceId());
              statement.setInt(2, slot);
              statement.addBatch();
            }
            statement.executeBatch();
          }
        } else if (slots != capacity.slots())
          throw new IllegalStateException(
              "Instance slot count differs from provisioned capacity; stop writers before changing"
                  + " it");
        connection.commit();
      } catch (SQLException | RuntimeException failure) {
        connection.rollback();
        throw failure;
      } finally {
        connection.setAutoCommit(true);
      }
    } catch (SQLException failure) {
      throw new TenantRegistryException(
          "Could not provision lifecycle admission capacity", failure);
    }
  }

  /** Provisioning only: runtime can coordinate existing slots but cannot create or delete them. */
  public void grantRuntimeAccess(String role) {
    if (role == null || !role.matches("[a-z][a-z0-9_]{0,62}"))
      throw new IllegalArgumentException("Invalid runtime database role");
    String quoted = "\"" + role + "\"";
    try (Connection connection = controlPlane.getConnection();
        var statement = connection.createStatement()) {
      requireIndependent(connection);
      statement.execute("GRANT USAGE ON SCHEMA public TO " + quoted);
      statement.execute("GRANT SELECT ON public.lifecycle_admission_instance TO " + quoted);
      statement.execute("GRANT SELECT,UPDATE ON public.lifecycle_admission_lease TO " + quoted);
    } catch (SQLException failure) {
      throw new TenantRegistryException(
          "Could not grant runtime lifecycle admission access", failure);
    }
  }

  /** Reads the one authoritative slot count and connection budget, never service-local values. */
  public Optional<Capacity> capacity(UUID instanceId) {
    String sql =
        "SELECT i.connection_budget,count(l.slot) FROM lifecycle_admission_instance i JOIN"
            + " lifecycle_admission_lease l USING(instance_id) WHERE i.instance_id = ? GROUP BY"
            + " i.connection_budget";
    try (Connection connection = controlPlane.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      requireIndependent(connection);
      statement.setObject(1, instanceId);
      try (var rows = statement.executeQuery()) {
        return rows.next()
            ? Optional.of(new Capacity(instanceId, rows.getInt(2), rows.getInt(1)))
            : Optional.empty();
      }
    } catch (SQLException failure) {
      throw new TenantRegistryException("Could not read lifecycle admission capacity", failure);
    }
  }

  public Optional<Claim> claim(
      UUID instanceId, UUID tenantId, UUID operationId, Duration claimDuration) {
    long millis = positiveMillis(claimDuration);
    UUID token = UUID.randomUUID();
    String sql =
        "WITH candidate AS (SELECT instance_id,slot FROM lifecycle_admission_lease WHERE"
            + " instance_id = ? AND (state = 'free' OR (state = 'claimed' AND claim_deadline <="
            + " clock_timestamp()) OR (state = 'registered' AND expires_at <= clock_timestamp()))"
            + " ORDER BY slot FOR UPDATE SKIP LOCKED LIMIT 1) UPDATE lifecycle_admission_lease l"
            + " SET holder_token = ?,tenant_id = ?,operation_id = ?,state ="
            + " 'claimed',claim_deadline = clock_timestamp() + (? * interval '1"
            + " millisecond'),expires_at = NULL,updated_at = clock_timestamp() FROM candidate c"
            + " WHERE l.instance_id = c.instance_id AND l.slot = c.slot RETURNING l.slot";
    try (Connection connection = controlPlane.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      requireIndependent(connection);
      statement.setObject(1, instanceId);
      statement.setObject(2, token);
      statement.setObject(3, tenantId);
      statement.setObject(4, operationId);
      statement.setLong(5, millis);
      try (var rows = statement.executeQuery()) {
        return rows.next()
            ? Optional.of(new Claim(instanceId, rows.getInt(1), token))
            : Optional.empty();
      }
    } catch (SQLException failure) {
      throw new TenantRegistryException("Could not claim lifecycle admission slot", failure);
    }
  }

  /** Register before taking any tenant guard. Remaining time was measured on the tenant server. */
  public boolean register(Claim claim, Duration remainingServerTime, Duration margin) {
    long millis = Math.addExact(positiveMillis(remainingServerTime), positiveMillis(margin));
    String sql =
        "UPDATE lifecycle_admission_lease SET state = 'registered',expires_at = clock_timestamp() +"
            + " (? * interval '1 millisecond'),updated_at = clock_timestamp() WHERE instance_id = ?"
            + " AND slot = ? AND holder_token = ? AND state = 'claimed' AND claim_deadline >"
            + " clock_timestamp()";
    try (Connection connection = controlPlane.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      requireIndependent(connection);
      statement.setLong(1, millis);
      statement.setObject(2, claim.instanceId());
      statement.setInt(3, claim.slot());
      statement.setObject(4, claim.holderToken());
      return statement.executeUpdate() == 1;
    } catch (SQLException failure) {
      throw new TenantRegistryException("Could not register lifecycle admission deadline", failure);
    }
  }

  /** Call only after commit or rollback. An old holder cannot free its replacement's slot. */
  public boolean release(Claim claim) {
    String sql =
        "UPDATE lifecycle_admission_lease SET state = 'free',holder_token = NULL,tenant_id ="
            + " NULL,operation_id = NULL,claim_deadline = NULL,expires_at = NULL,updated_at ="
            + " clock_timestamp() WHERE instance_id = ? AND slot = ? AND holder_token = ?";
    try (Connection connection = controlPlane.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      requireIndependent(connection);
      statement.setObject(1, claim.instanceId());
      statement.setInt(2, claim.slot());
      statement.setObject(3, claim.holderToken());
      return statement.executeUpdate() == 1;
    } catch (SQLException failure) {
      throw new TenantRegistryException("Could not release lifecycle admission slot", failure);
    }
  }

  private static long positiveMillis(Duration duration) {
    long millis = Objects.requireNonNull(duration, "duration").toMillis();
    if (millis < 1 || millis > Duration.ofHours(1).toMillis())
      throw new IllegalArgumentException(
          "Admission duration must be positive and at most one hour");
    return millis;
  }

  private static void requireIndependent(Connection connection) throws SQLException {
    if (!connection.getAutoCommit())
      throw new IllegalStateException(
          "Lifecycle admission metadata requires a dedicated, non-enlisted control-plane"
              + " datasource");
  }
}
