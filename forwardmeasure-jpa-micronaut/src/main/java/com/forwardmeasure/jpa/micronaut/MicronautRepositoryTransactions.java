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
package com.forwardmeasure.jpa.micronaut;

import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Singleton;
import java.util.Objects;
import java.util.function.Supplier;
import org.hibernate.Session;

/** Framework transaction adapter; consumers never handle Hibernate sessions. */
@Singleton
public final class MicronautRepositoryTransactions {
  private final TransactionOperations<Session> transactions;

  public MicronautRepositoryTransactions(TransactionOperations<Session> transactions) {
    this.transactions = Objects.requireNonNull(transactions, "transactions");
  }

  public <T> T execute(Supplier<T> work) {
    var definition =
        new io.micronaut.transaction.support.DefaultTransactionDefinition(
            io.micronaut.transaction.TransactionDefinition.Propagation.REQUIRES_NEW);
    return transactions.execute(definition, status -> work.get());
  }
}
