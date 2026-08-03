package com.forwardmeasure.jpa.locking;

/**
 * Acquires tenant-local named mutexes inside an existing transaction.
 *
 * <p>Framework adapters enforce mandatory transaction propagation. The lock
 * remains held until that surrounding transaction commits or rolls back.
 */
public interface SystemLockService {

    void acquire(String lockName);
}
