package com.forwardmeasure.jpa.identity;

import com.forwardmeasure.jpa.core.AuthorizableResource;

public interface OwnableResource extends AuthorizableResource {

    Actor getOwner();
}
