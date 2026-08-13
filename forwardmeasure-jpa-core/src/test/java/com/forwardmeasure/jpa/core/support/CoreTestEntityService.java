package com.forwardmeasure.jpa.core.support;

import com.forwardmeasure.jpa.core.service.impl.AuditedEntityServiceImpl;

public class CoreTestEntityService
        extends AuditedEntityServiceImpl<
                CoreTestEntity,
                Long,
                CoreTestEntityRepository> {

    public CoreTestEntityService(CoreTestEntityRepository repository) {
        super(repository);
    }
}
