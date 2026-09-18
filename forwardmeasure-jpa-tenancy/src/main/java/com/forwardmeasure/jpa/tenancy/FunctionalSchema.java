package com.forwardmeasure.jpa.tenancy;

/**
 * The schema a given product owns within one tenant's own {@link TenantDatabase} - the second,
 * fixed axis of tenant routing alongside the dynamically-resolved {@link TenantDatabase} itself.
 * Unlike {@link TenantDatabase} (resolved per request from a {@link TenantId}), a product's own
 * functional schema is a compile-time constant: fowf always uses {@link #OPENWORKFLOW}, fei always
 * uses {@link #ENTITY_INTELLIGENCE}, and so on - never resolved dynamically per call.
 *
 * <p>No cross-schema foreign keys between these are expected: products integrate over REST/gRPC/
 * CloudEvents, not shared SQL joins, and cross-database foreign keys are impossible in Postgres
 * regardless - this enum only names the schemas, it does not imply they may reference each other.
 */
public enum FunctionalSchema {
  OPENWORKFLOW("openworkflow"),
  ENTITY_INTELLIGENCE("entity_intelligence"),
  DECISION_INTELLIGENCE("decision_intelligence"),
  AGENT_OS("agent_os"),

  /**
   * Reserved, not yet used by any real migrator - forwardmeasure-data-streaming has zero owned
   * persistence today (confirmed: its only JDBC-adjacent code is a generic source/sink connector
   * for arbitrary external customer databases, not its own schema). Kept here so the
   * functional-schema naming convention is settled in one place before FDS ever needs it, rather
   * than invented ad hoc later.
   */
  DATA_STREAMING("data_streaming");

  private final String schemaName;

  FunctionalSchema(String schemaName) {
    this.schemaName = schemaName;
  }

  public String schemaName() {
    return schemaName;
  }

  @Override
  public String toString() {
    return schemaName;
  }
}
