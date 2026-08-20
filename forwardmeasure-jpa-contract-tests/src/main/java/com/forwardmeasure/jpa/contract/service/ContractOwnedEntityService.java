package com.forwardmeasure.jpa.contract.service;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import com.forwardmeasure.jpa.contract.repository.ContractOwnedEntityRepository;
import com.forwardmeasure.jpa.identity.service.impl.OwnedEntityServiceImpl;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ContractOwnedEntityService
    extends OwnedEntityServiceImpl<ContractOwnedEntity, Long, ContractOwnedEntityRepository> {

  @Inject
  public ContractOwnedEntityService(ContractOwnedEntityRepository repository) {
    super(repository);
  }
}
