package com.forwardmeasure.jpa.liquibase;

public final class TenantRegistryException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public TenantRegistryException(String message, Throwable cause) {
    super(message, cause);
  }
}
