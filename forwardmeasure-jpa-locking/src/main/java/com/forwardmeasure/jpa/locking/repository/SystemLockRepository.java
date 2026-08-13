package com.forwardmeasure.jpa.locking.repository;

import com.forwardmeasure.jpa.core.repository.AbstractBaseRepository;
import com.forwardmeasure.jpa.locking.entity.SystemLock;
import jakarta.inject.Singleton;
import jakarta.persistence.LockModeType;
import java.util.Objects;

/** Standard-JPA repository for transaction-scoped named system locks. */
@Singleton
public class SystemLockRepository
        extends AbstractBaseRepository<SystemLock, String> {

    public SystemLock acquireLock(String lockName) {
        String requiredName = Objects.requireNonNull(lockName, "lockName");
        if (requiredName.isBlank()) {
            throw new IllegalArgumentException("lockName must not be blank");
        }

        SystemLock lock = findById(
                requiredName, LockModeType.PESSIMISTIC_WRITE);
        if (lock == null) {
            throw new IllegalStateException(
                    "System lock row not found: " + requiredName
                            + " — ensure the seed changeset has been applied");
        }
        return lock;
    }
}
