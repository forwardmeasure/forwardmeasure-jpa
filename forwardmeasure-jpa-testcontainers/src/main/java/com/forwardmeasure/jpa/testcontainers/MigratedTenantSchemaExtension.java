package com.forwardmeasure.jpa.testcontainers;

import com.forwardmeasure.jpa.tenancy.TenantId;
import com.forwardmeasure.jpa.tenancy.TenantSchema;
import com.forwardmeasure.testcontainers.junit.postgresql.PostgreSqlContainerExtension;
import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.UUID;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Backs {@link WithMigratedTenantSchema}. Runs after {@link PostgreSqlContainerExtension}'s own
 * {@code beforeAll} (JUnit Jupiter registers meta-annotated extensions in declaration order, and
 * {@code @WithMigratedTenantSchema} declares {@code @WithPostgreSqlContainer} first), so the
 * container this extension reads via {@link PostgreSqlContainerExtension#containerFor} is always
 * already running.
 *
 * <p>{@link BeforeAllCallback}, not {@link org.junit.jupiter.api.extension.BeforeEachCallback} -
 * the schema is migrated exactly once per test class and reused for every {@code @Test} method,
 * mirroring how {@link PostgreSqlContainerExtension} itself already shares one container per class
 * rather than starting a fresh one per method. An earlier version of this class ran the full
 * migration chain (schema creation, Liquibase locking, checksum validation, one DB round trip per
 * changelog) in {@code beforeEach} instead - real, measurable, and entirely avoidable overhead
 * repeated once per test method for no benefit a class-level schema doesn't already provide.
 *
 * <p>Because the schema is now shared across every method in a class, tests that need a
 * method-local starting point must arrange their own fixtures accordingly (distinct keys/values per
 * test, or an explicit cleanup step) rather than relying on an empty schema at the start of each
 * method.
 */
public final class MigratedTenantSchemaExtension implements BeforeAllCallback, ParameterResolver {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(MigratedTenantSchemaExtension.class);
  private static final String SCHEMA_KEY = "migratedTenantSchema";

  @Override
  public void beforeAll(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    WithMigratedTenantSchema annotation = testClass.getAnnotation(WithMigratedTenantSchema.class);
    if (annotation == null) {
      throw new IllegalStateException(
          testClass.getName() + " is not annotated with @WithMigratedTenantSchema");
    }

    PostgreSqlTestContainer container = PostgreSqlContainerExtension.containerFor(testClass);
    TenantSchema schema =
        TenantSchemaMigrations.migrate(
            container, new TenantId(UUID.randomUUID()), annotation.changelogs());

    context.getStore(NAMESPACE).put(SCHEMA_KEY, schema);
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    return parameterContext.getParameter().getType().equals(TenantSchema.class);
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    TenantSchema schema = extensionContext.getStore(NAMESPACE).get(SCHEMA_KEY, TenantSchema.class);
    if (schema == null) {
      throw new ParameterResolutionException(
          "No migrated TenantSchema available for this test - has beforeAll run yet?");
    }
    return schema;
  }
}
