package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.locking.RepositorySystemLockService;
import com.forwardmeasure.jpa.locking.SystemLockRepository;
import com.forwardmeasure.jpa.locking.SystemLockService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Quarkus lock service that refuses acquisition outside an active transaction. */
@ApplicationScoped
@DefaultBean
public class QuarkusSystemLockService implements SystemLockService {

    private final RepositorySystemLockService delegate;

    @Inject
    public QuarkusSystemLockService(SystemLockRepository repository) {
        this.delegate = new RepositorySystemLockService(repository);
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void acquire(String lockName) {
        delegate.acquire(lockName);
    }
}
