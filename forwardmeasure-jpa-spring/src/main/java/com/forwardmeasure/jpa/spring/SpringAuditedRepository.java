package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.core.AuditedEntity;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface SpringAuditedRepository<
        T extends AuditedEntity<I>, I extends Serializable>
        extends JpaRepository<T, I> {

    Optional<T> findByUuid(UUID uuid);

    List<T> findByUuidIn(Collection<UUID> uuids);

    boolean existsByUuid(UUID uuid);

    long deleteByUuid(UUID uuid);
}
