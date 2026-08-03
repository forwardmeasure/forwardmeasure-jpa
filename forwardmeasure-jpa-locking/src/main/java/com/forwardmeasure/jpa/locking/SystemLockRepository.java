package com.forwardmeasure.jpa.locking;

/** Persistence port for acquiring a named row lock. */
public interface SystemLockRepository {

    /**
     * Acquires the named lock until the caller's transaction terminates.
     *
     * @throws IllegalStateException when the application did not provision
     *         the named lock row
     */
    void acquire(String lockName);
}
