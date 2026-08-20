package com.forwardmeasure.jpa.locking.service;

import com.forwardmeasure.jpa.core.service.AbstractBaseService;
import com.forwardmeasure.jpa.locking.entity.SystemLock;

/** Application-facing transaction-scoped named-lock operations. */
public interface SystemLockService extends AbstractBaseService<SystemLock, String> {

  /**
   * Acquires the named pessimistic row lock for the caller's transaction.
   *
   * @throws jakarta.transaction.TransactionalException when no caller transaction is active
   * @throws IllegalStateException when the lock row was not provisioned
   */
  SystemLock acquireLock(String lockName);
}
