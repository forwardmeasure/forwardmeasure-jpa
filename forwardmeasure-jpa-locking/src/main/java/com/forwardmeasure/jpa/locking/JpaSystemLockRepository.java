package com.forwardmeasure.jpa.locking;

import com.forwardmeasure.jpa.locking.entity.SystemLock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;

/** Standard-JPA pessimistic-write implementation. */
public class JpaSystemLockRepository implements SystemLockRepository {

    private final EntityManager entityManager;

    public JpaSystemLockRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(
                entityManager,
                "entityManager");
    }

    @Override
    public void acquire(String lockName) {
        String requiredName = Objects.requireNonNull(lockName, "lockName");
        if (requiredName.isBlank()) {
            throw new IllegalArgumentException("lockName must not be blank");
        }
        SystemLock lock = entityManager.find(
                SystemLock.class,
                requiredName,
                LockModeType.PESSIMISTIC_WRITE);
        if (lock == null) {
            throw new IllegalStateException(
                    "System lock row does not exist: " + requiredName);
        }
    }
}
