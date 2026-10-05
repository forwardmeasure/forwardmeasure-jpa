/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at https://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.forwardmeasure.jpa.core.repository;

import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import java.util.function.Function;
import org.hibernate.SessionFactory;

/**
 * Independent tenant transactions for asynchronous repository work outside request transactions.
 */
public final class JpaRepositoryTransactions {
  private final SessionFactory sessions;

  public JpaRepositoryTransactions(EntityManagerFactory factory) {
    sessions = Objects.requireNonNull(factory, "factory").unwrap(SessionFactory.class);
  }

  public <T> T execute(String tenantId, Function<JpaRepositoryContext, T> work) {
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(work, "work");
    try (var session = sessions.withOptions().tenantIdentifier((Object) tenantId).openSession()) {
      var transaction = session.beginTransaction();
      try {
        T result = work.apply(new JpaRepositoryContext(session));
        transaction.commit();
        return result;
      } catch (RuntimeException | Error failure) {
        try {
          if (transaction.isActive()) transaction.rollback();
        } catch (RuntimeException rollbackFailure) {
          failure.addSuppressed(rollbackFailure);
        }
        throw failure;
      }
    }
  }
}
