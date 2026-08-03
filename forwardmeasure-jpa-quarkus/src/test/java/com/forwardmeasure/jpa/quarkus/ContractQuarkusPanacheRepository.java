package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.contract.entity.ContractOwnedEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ContractQuarkusPanacheRepository
        implements QuarkusOwnedRepository<ContractOwnedEntity, Long> {
}
