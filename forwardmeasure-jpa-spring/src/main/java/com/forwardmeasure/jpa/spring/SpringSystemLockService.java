package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.locking.RepositorySystemLockService;
import com.forwardmeasure.jpa.locking.SystemLockRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Spring lock service that refuses acquisition outside an active transaction. */
public class SpringSystemLockService extends RepositorySystemLockService {

    public SpringSystemLockService(SystemLockRepository repository) {
        super(repository);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String lockName) {
        super.acquire(lockName);
    }
}
