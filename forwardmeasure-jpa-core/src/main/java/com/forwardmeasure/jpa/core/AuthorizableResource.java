package com.forwardmeasure.jpa.core;

/**
 * A persistent resource that exposes a stable, externally usable identifier
 * and an authorization resource type.
 */
public interface AuthorizableResource {

    String getResourceId();

    String getResourceType();
}
