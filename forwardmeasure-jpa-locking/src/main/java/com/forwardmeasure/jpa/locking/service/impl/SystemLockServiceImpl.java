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
package com.forwardmeasure.jpa.locking.service.impl;

import com.forwardmeasure.jpa.core.service.impl.AbstractBaseServiceImpl;
import com.forwardmeasure.jpa.locking.entity.SystemLock;
import com.forwardmeasure.jpa.locking.repository.SystemLockRepository;
import com.forwardmeasure.jpa.locking.service.SystemLockService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

/** Standard-JPA system-lock service shared by every application host. */
@Singleton
public class SystemLockServiceImpl
    extends AbstractBaseServiceImpl<SystemLock, String, SystemLockRepository>
    implements SystemLockService {

  @Inject
  public SystemLockServiceImpl(SystemLockRepository repository) {
    super(repository);
  }

  @Override
  @Transactional(Transactional.TxType.MANDATORY)
  public SystemLock acquireLock(String lockName) {
    return repository().acquireLock(lockName);
  }
}
