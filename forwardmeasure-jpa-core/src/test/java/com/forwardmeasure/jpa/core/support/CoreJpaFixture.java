package com.forwardmeasure.jpa.core.support;

import com.forwardmeasure.testcontainers.postgresql.PostgreSqlTestContainer;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public final class CoreJpaFixture implements AutoCloseable {

    private final SessionFactory sessions;

    private CoreJpaFixture(SessionFactory sessions) {
        this.sessions = sessions;
    }

    public static CoreJpaFixture create(PostgreSqlTestContainer database) {
        String schema = "core_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        database.createSchema(schema);

        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(CoreTestCategory.class);
        configuration.addAnnotatedClass(CoreTestEntity.class);
        Map<String, String> properties = Map.of(
                "jakarta.persistence.jdbc.url", database.hostJdbcUrl(),
                "jakarta.persistence.jdbc.user", database.username(),
                "jakarta.persistence.jdbc.password", database.password(),
                "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
                "hibernate.default_schema", schema,
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.show_sql", "false");
        properties.forEach(configuration::setProperty);
        return new CoreJpaFixture(configuration.buildSessionFactory());
    }

    public <T> T transaction(Function<Context, T> work) {
        try (Session session = sessions.openSession()) {
            var transaction = session.beginTransaction();
            try {
                T result = work.apply(context(session));
                transaction.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw failure;
            }
        }
    }

    public <T> T session(Function<Context, T> work) {
        try (Session session = sessions.openSession()) {
            return work.apply(context(session));
        }
    }

    public SessionFactory sessions() {
        return sessions;
    }

    private Context context(Session session) {
        CoreTestEntityRepository repository = new CoreTestEntityRepository();
        repository.bindPersistenceContext(session);
        return new Context(
                session,
                repository,
                new CoreTestEntityService(repository));
    }

    @Override
    public void close() {
        sessions.close();
    }

    public record Context(
            Session entityManager,
            CoreTestEntityRepository repository,
            CoreTestEntityService service) {
    }
}
