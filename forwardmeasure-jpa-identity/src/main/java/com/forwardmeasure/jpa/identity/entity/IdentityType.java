package com.forwardmeasure.jpa.identity.entity;

import java.util.Arrays;

public enum IdentityType {
  HUMAN,
  SERVICE;

  public static IdentityType fromCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Identity type must not be blank");
    }
    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported identity type: " + code));
  }
}
