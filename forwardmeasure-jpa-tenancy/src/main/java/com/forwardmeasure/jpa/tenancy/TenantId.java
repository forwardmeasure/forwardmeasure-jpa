package com.forwardmeasure.jpa.tenancy;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

  public TenantId {
    Objects.requireNonNull(value, "value");
  }

  public static TenantId parse(String value) {
    return new TenantId(UUID.fromString(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
