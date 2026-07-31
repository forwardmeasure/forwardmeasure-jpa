package com.forwardmeasure.jpa.micronaut;

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.Entity;

/**
 * Generates Micronaut introspection metadata for the shared identity entities
 * without introducing a Micronaut dependency into the portable identity
 * module.
 */
@Introspected(
        packages = "com.forwardmeasure.jpa.identity",
        includedAnnotations = Entity.class)
final class ForwardMeasureJpaEntityIntrospection {

    private ForwardMeasureJpaEntityIntrospection() {
    }
}
