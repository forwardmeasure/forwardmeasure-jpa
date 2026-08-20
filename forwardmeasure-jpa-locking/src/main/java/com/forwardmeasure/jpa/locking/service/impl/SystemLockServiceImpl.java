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
