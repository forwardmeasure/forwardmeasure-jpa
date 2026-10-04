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
package com.forwardmeasure.jpa.locking.repository;

import com.forwardmeasure.jpa.core.repository.AbstractBaseRepository;
import com.forwardmeasure.jpa.locking.entity.SystemLock;
import jakarta.inject.Singleton;
import jakarta.persistence.LockModeType;
import java.util.Objects;

/** Standard-JPA repository for transaction-scoped named system locks. */
@Singleton
public class SystemLockRepository extends AbstractBaseRepository<SystemLock, String> {

  public SystemLock acquireLock(String lockName) {
    String requiredName = Objects.requireNonNull(lockName, "lockName");
    if (requiredName.isBlank()) {
      throw new IllegalArgumentException("lockName must not be blank");
    }

    SystemLock lock = findById(requiredName, LockModeType.PESSIMISTIC_WRITE);
    if (lock == null) {
      throw new IllegalStateException(
          "System lock row not found: "
              + requiredName
              + " — ensure the seed changeset has been applied");
    }
    return lock;
  }
}
