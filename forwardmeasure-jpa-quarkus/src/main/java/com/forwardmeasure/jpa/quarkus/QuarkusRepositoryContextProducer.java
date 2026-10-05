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
package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.core.repository.JpaRepositoryContext;
import com.forwardmeasure.jpa.core.repository.JpaRepositoryTransactions;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/** Adapts Quarkus's managed context for repository construction. */
@ApplicationScoped
@IfBuildProperty(
    name = "quarkus.hibernate-orm.enabled",
    stringValue = "true",
    enableIfMissing = true)
public class QuarkusRepositoryContextProducer {
  @Produces
  @Singleton
  @DefaultBean
  JpaRepositoryTransactions repositoryTransactions(EntityManagerFactory entityManagerFactory) {
    return new JpaRepositoryTransactions(entityManagerFactory);
  }

  @Produces
  @Singleton
  @DefaultBean
  JpaRepositoryContext repositoryContext(EntityManager entityManager) {
    return new JpaRepositoryContext(entityManager);
  }
}
