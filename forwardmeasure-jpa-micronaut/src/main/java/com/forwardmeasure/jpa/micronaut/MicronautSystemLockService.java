package com.forwardmeasure.jpa.micronaut;

import com.forwardmeasure.jpa.locking.RepositorySystemLockService;
import com.forwardmeasure.jpa.locking.SystemLockRepository;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/** Micronaut lock service that requires an existing transaction. */
@Singleton
@Secondary
public class MicronautSystemLockService extends RepositorySystemLockService {

    public MicronautSystemLockService(SystemLockRepository repository) {
        super(repository);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String lockName) {
        super.acquire(lockName);
    }
}
