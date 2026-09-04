package com.forwardmeasure.jpa.testcontainers;

import com.forwardmeasure.jpa.liquibase.TenantSchemaMigrator;
import com.forwardmeasure.testcontainers.junit.postgresql.WithPostgreSqlContainer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Hands every {@code @Test} method a real, already-migrated {@link
 * com.forwardmeasure.jpa.tenancy.TenantSchema} - a fresh Postgres schema, with {@link
 * TenantSchemaMigrator#DEFAULT_CHANGELOG} (the framework base) plus, in order, every changelog
 * listed in {@link #changelogs()} already applied. One real {@code PostgreSQLContainer} is shared
 * for the whole test class (via the meta-annotated {@link WithPostgreSqlContainer}); the schema
 * itself - and therefore the migration - is fresh per test method, matching how every consumer of
 * this annotation previously built its own throwaway schema by hand once per test.
 *
 * <p>Request {@link com.forwardmeasure.jpa.tenancy.TenantSchema} and/or {@link
 * com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer} as {@code @Test} method
 * parameters - both are resolved by extensions this annotation pulls in. Callers still build their
 * own {@code EntityManagerFactory}/domain wiring against the returned schema (this module has no
 * opinion on which {@code @Entity} classes a given test needs); it only owns getting a real,
 * correctly-migrated schema in front of that wiring.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WithPostgreSqlContainer
@ExtendWith(MigratedTenantSchemaExtension.class)
public @interface WithMigratedTenantSchema {

  /**
   * Extra changelogs applied, in order, after the framework base. Classpath-relative paths, passed
   * straight through to {@link TenantSchemaMigrator}'s own changelog-path constructor.
   */
  String[] changelogs() default {};
}
