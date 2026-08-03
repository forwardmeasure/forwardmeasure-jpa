package com.forwardmeasure.jpa.locking;

import java.util.Objects;

/** Provider-neutral lock service implementation. */
public class RepositorySystemLockService implements SystemLockService {

    private final SystemLockRepository repository;

    public RepositorySystemLockService(SystemLockRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void acquire(String lockName) {
        repository.acquire(lockName);
    }
}
