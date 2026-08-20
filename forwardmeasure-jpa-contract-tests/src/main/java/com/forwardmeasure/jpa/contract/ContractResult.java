package com.forwardmeasure.jpa.contract;

import java.util.UUID;

public record ContractResult(
    Long actorId, UUID actorUuid, Long entityId, UUID entityUuid, Integer entityVersion) {}
