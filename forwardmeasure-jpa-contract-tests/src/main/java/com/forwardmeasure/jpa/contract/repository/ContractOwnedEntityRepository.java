package com.forwardmeasure.jpa.contract.repository;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.identity.repository.AbstractOwnedEntityRepository;
import jakarta.inject.Singleton;

@Singleton
public class ContractOwnedEntityRepository
    extends AbstractOwnedEntityRepository<ContractOwnedEntity, Long> {}
