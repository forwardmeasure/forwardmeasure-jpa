package com.forwardmeasure.jpa.contract;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.identity.repository.OwnedEntityRepository;
import com.forwardmeasure.jpa.identity.service.AbstractOwnedEntityService;

/** Portable service used by every framework's real-database contract. */
public final class ContractOwnedEntityService
        extends AbstractOwnedEntityService<
                ContractOwnedEntity,
                Long,
                OwnedEntityRepository<ContractOwnedEntity, Long>> {

    public ContractOwnedEntityService(
            OwnedEntityRepository<ContractOwnedEntity, Long> repository) {
        super(repository);
    }
}
