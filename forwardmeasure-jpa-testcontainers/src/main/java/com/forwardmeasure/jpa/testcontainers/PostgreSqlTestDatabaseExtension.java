package com.forwardmeasure.jpa.testcontainers;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * JUnit extension that supplies one real PostgreSQL container per test class.
 */
public final class PostgreSqlTestDatabaseExtension
        implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(
                    PostgreSqlTestDatabaseExtension.class);
    @Override
    public void beforeAll(ExtensionContext context) {
        store(context).put(key(context), new PostgreSqlTestDatabase().start());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        PostgreSqlTestDatabase database =
                store(context).remove(
                        key(context), PostgreSqlTestDatabase.class);
        if (database != null) {
            database.close();
        }
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType()
                .equals(PostgreSqlTestDatabase.class);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext)
            throws ParameterResolutionException {
        PostgreSqlTestDatabase database =
                store(extensionContext).get(
                        key(extensionContext), PostgreSqlTestDatabase.class);
        if (database == null) {
            throw new ParameterResolutionException(
                    "PostgreSQL fixture is not available");
        }
        return database;
    }

    private ExtensionContext.Store store(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE);
    }

    private Class<?> key(ExtensionContext context) {
        return context.getRequiredTestClass();
    }
}
