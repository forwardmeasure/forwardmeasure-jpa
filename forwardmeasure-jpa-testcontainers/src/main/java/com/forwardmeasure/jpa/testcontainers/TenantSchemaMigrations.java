package com.forwardmeasure.jpa.testcontainers;

import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;

/**
 * The imperative counterpart to {@link WithMigratedTenantSchema}: creates a tenant schema and
 * applies the same migration chain (framework base, then every listed changelog, in order), but as
 * a plain method call rather than a JUnit 5 extension.
 *
 * <p>{@link MigratedTenantSchemaExtension} itself calls this. It also exists for callers that
 * cannot use the annotation because their tenant id is fixed by something else first - a deployment
 * leaf's own {@code QuarkusTestResourceLifecycleManager}/Spring {@code @BeforeAll}/Micronaut {@code
 * TestPropertyProvider} fixture must provision the same tenant id in both a real Keycloak
 * organization (via that repo's own {@code KeycloakOrganizationFixture}) and the database schema,
 * and needs the schema ready before the framework's own application context boots - earlier than
 * any {@code BeforeEachCallback} runs. Before this class existed, every one of those fixtures
 * duplicated this exact migration sequence inline.
 */
public final class TenantSchemaMigrations {

  private TenantSchemaMigrations() {}

  /**
   * Creates the schema for {@code tenantId} and applies the framework base changelog followed by
   * {@code changelogs}, in order.
   *
   * @return the {@link TenantSchema} that was migrated, so callers that only had a {@link TenantId}
   *     don't need to re-derive it
   */
  public static TenantSchema migrate(
      PostgreSqlTestContainer database, TenantId tenantId, String... changelogs) {
    TenantSchema schema = TenantSchema.forTenant(tenantId);
    database.createSchema(schema.value());
    ClassLoader classLoader = TenantSchemaMigrations.class.getClassLoader();
    new TenantSchemaMigrator(database.dataSource()).migrate(schema);
    for (String changelog : changelogs) {
      new TenantSchemaMigrator(database.dataSource(), changelog, classLoader).migrate(schema);
    }
    return schema;
  }
}
