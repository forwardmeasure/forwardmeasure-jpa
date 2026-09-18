package com.forwardmeasure.jpa.quarkus;

import com.forwardmeasure.jpa.datasource.TenantDataSourceRegistry;
import com.forwardmeasure.jpa.datasource.TenantDataSourceTemplate;
import com.forwardmeasure.jpa.liquibase.TenantDatabaseResolver;
import com.forwardmeasure.jpa.liquibase.TenantRegistry;
import com.forwardmeasure.jpa.tenancy.FunctionalSchema;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QuarkusTenantDataSourceProducer {

  @Produces
  @Singleton
  @DefaultBean
  FunctionalSchema functionalSchema(
      @ConfigProperty(name = "forwardmeasure.jpa.functional-schema") String functionalSchema) {
    return FunctionalSchema.valueOf(functionalSchema.toUpperCase(Locale.ROOT));
  }

  @Produces
  @Singleton
  @DefaultBean
  TenantDataSourceRegistry tenantDataSourceRegistry(
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.host") String host,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.port", defaultValue = "5432")
          int port,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.username") String username,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.password") String password,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.minimum-idle")
          Optional<Integer> minimumIdle,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.maximum-pool-size")
          Optional<Integer> maximumPoolSize,
      @ConfigProperty(name = "forwardmeasure.jpa.tenant-database.idle-eviction-timeout-minutes")
          Optional<Long> idleEvictionTimeoutMinutes) {
    return new TenantDataSourceRegistry(
        new TenantDataSourceTemplate(
            "jdbc:postgresql://" + host + ":" + port + "/",
            username,
            password,
            minimumIdle.orElse(TenantDataSourceTemplate.DEFAULT_MINIMUM_IDLE),
            maximumPoolSize.orElse(TenantDataSourceTemplate.DEFAULT_MAXIMUM_POOL_SIZE),
            idleEvictionTimeoutMinutes
                .map(Duration::ofMinutes)
                .orElse(TenantDataSourceTemplate.DEFAULT_IDLE_EVICTION_TIMEOUT)));
  }

  void closeTenantDataSourceRegistry(@Disposes TenantDataSourceRegistry registry) {
    registry.close();
  }

  /**
   * @param dataSource the app's own default Agroal-managed {@code DataSource} (from {@code
   *     quarkus.datasource.*}) - already used only for Hibernate's own tenant-agnostic operations
   *     (dialect resolution, never real tenant data), and reused here for exactly the same reason:
   *     it must already point at the small platform/control-plane database this class's own {@code
   *     tenant_registry} table lives in, not any tenant's own database. One connection target
   *     serves both purposes; no separate platform-database config is needed.
   *     <p>Injected as {@link Instance} rather than directly: not every consumer of {@code
   *     forwardmeasure-jpa-quarkus} configures a datasource at all - some (e.g. {@code
   *     openworkflow-engine-kafka-streams-quarkus}) pull this module in only for {@code
   *     ActiveOrganizationProvider} with {@code quarkus.hibernate-orm.enabled: false} and no
   *     datasource whatsoever. A direct {@code DataSource} parameter makes Arc's build-time
   *     validation fail for those consumers even though nothing of theirs ever injects {@link
   *     TenantRegistry}/{@link TenantDatabaseResolver}. {@link Instance} is always resolvable at
   *     build time regardless of whether a {@code DataSource} bean exists; {@link Instance#get()}
   *     is only actually evaluated if this producer itself is ever invoked, which only happens for
   *     a real injection point that needs {@link TenantRegistry}.
   */
  @Produces
  @Singleton
  @DefaultBean
  TenantRegistry tenantRegistry(Instance<DataSource> dataSource) {
    return new TenantRegistry(dataSource.get());
  }

  @Produces
  @Singleton
  @DefaultBean
  TenantDatabaseResolver tenantDatabaseResolver(TenantRegistry registry) {
    return new TenantDatabaseResolver(registry);
  }
}
