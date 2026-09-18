package com.forwardmeasure.jpa.tenancy;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * A physical, JDBC/DDL-safe tenant identity - always a UUID, never a {@link Did} directly, since a
 * DID string cannot be relied on to fit Postgres's identifier constraints. {@link Did} is the
 * canonical, universal <em>business</em> tenant identity across this platform (Keycloak claims,
 * APIs, CloudEvents); this type is the internal value everything that routes to a physical database
 * or schema derives from it via {@link #forDid(Did)}.
 */
public record TenantId(UUID value) {

  public TenantId {
    Objects.requireNonNull(value, "value");
  }

  public static TenantId parse(String value) {
    return new TenantId(UUID.fromString(value));
  }

  /**
   * Deterministically derives a physical tenant identity from a {@link Did} - the same {@code did}
   * always produces the same {@link TenantId}, so a caller holding only a freshly-extracted DID (no
   * registry lookup yet) can still resolve a routing target. Uses the exact same derivation {@code
   * com.forwardmeasure.openworkflow.engine.api.TenantId}'s own legacy DID-fallback path already
   * uses ({@link UUID#nameUUIDFromBytes(byte[])} over the DID's UTF-8 bytes), so a given tenant's
   * DID resolves to the identical UUID everywhere in this platform, not just here.
   */
  public static TenantId forDid(Did did) {
    Objects.requireNonNull(did, "did");
    return new TenantId(UUID.nameUUIDFromBytes(did.value().getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
