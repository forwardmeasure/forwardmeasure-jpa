package com.forwardmeasure.jpa.tenancy;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validated per-tenant database identifier. Validation is intentionally strict so a database name
 * can be passed to JDBC connection URLs and DDL without becoming an SQL-injection surface - mirrors
 * {@link TenantSchema}'s own rationale exactly.
 *
 * <p>Named after the tenant's own alias ({@code forwardmeasure_<alias>}), not derived from its
 * {@link TenantId} - a tenant's UUID carries no readable information, and operators need to be able
 * to tell which physical database belongs to which tenant at a glance. This means {@code
 * TenantDatabase} is no longer a pure function of {@link TenantId}. Two real shapes of call site
 * exist: request-authenticated code (an HTTP/gRPC call whose JWT/Organization claims already carry
 * the tenant's alias - see {@code KeycloakOrganizationClaims#extract}, which now threads the alias
 * it already parses into {@code ActiveOrganization} instead of discarding it) can call {@link
 * #forAlias(String)} directly, no lookup needed. Code that genuinely only ever has a bare {@link
 * TenantId} (a Kafka-projected event, a Pekko actor recovering from its own journal after a
 * restart, a gRPC caller sending only a raw tenant-id header) has no alias to work with and needs a
 * real lookup against the tenant registry - see {@code TenantRegistry#resolve} (a real, once-per-
 * tenant database query) and {@code TenantDatabaseResolver} (the same lookup, cached; both in
 * {@code forwardmeasure-jpa-liquibase}).
 *
 * <p>Sibling of {@link TenantSchema}, not a replacement for it: this identifies <em>which tenant's
 * own Postgres database</em> a connection targets, while {@link FunctionalSchema} identifies which
 * product's schema within that database - two independent axes. See {@code
 * TenantDataSourceRegistry} for how a resolved {@code TenantDatabase} becomes a real connection
 * pool.
 */
public record TenantDatabase(String value) {

  /**
   * The Hibernate {@code CurrentTenantIdentifierResolver}-style sentinel for "no tenant is bound" -
   * deliberately does not start with {@code forwardmeasure_}, so it can never construct a valid
   * {@code TenantDatabase} and can never be used to obtain a real tenant connection. Mirrors {@code
   * TenantSchema#UNBOUND_IDENTIFIER} exactly.
   */
  public static final String UNBOUND_IDENTIFIER = "unbound_tenant";

  private static final String PREFIX = "forwardmeasure_";

  // Postgres's own identifier limit is 63 bytes; PREFIX is 15, leaving 48 for the alias - safely
  // bounded well below that ceiling. Lowercase letters/digits/underscore/hyphen only: DDL in
  // OpenWorkflowTenantMigrator always double-quotes this value, so hyphens are safe to allow (a
  // real, common convention in tenant slugs), but nothing that could break out of a quoted
  // identifier (quotes, whitespace, semicolons) is permitted.
  private static final Pattern ALIAS = Pattern.compile("[a-z][a-z0-9_-]{0,47}");

  public TenantDatabase {
    Objects.requireNonNull(value, "value");
    value = value.toLowerCase(Locale.ROOT);
    if (!value.startsWith(PREFIX) || !ALIAS.matcher(value.substring(PREFIX.length())).matches()) {
      throw new IllegalArgumentException(
          "Tenant database must be " + PREFIX + "{a safe tenant alias}");
    }
  }

  /**
   * The only real construction path: a tenant's own alias, exactly as recorded in {@code
   * TenantRegistry} and used to derive its {@link Did}/{@link TenantId} (see {@code
   * Did.parse("did:web:" + alias + "." + domain)} at every migration Job's own call site) - never a
   * second, independently-typed alias.
   */
  public static TenantDatabase forAlias(String alias) {
    Objects.requireNonNull(alias, "alias");
    return new TenantDatabase(PREFIX + alias);
  }

  /**
   * The tenant alias this database belongs to, with the {@code forwardmeasure_} prefix stripped.
   */
  public String alias() {
    return value.substring(PREFIX.length());
  }

  @Override
  public String toString() {
    return value;
  }
}
