package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.contract.ContractOwnedEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ContractQuarkusPanacheRepository
        implements QuarkusOwnedRepository<ContractOwnedEntity, Long> {
}
