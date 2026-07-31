package com.forwardmeasure.jpa.spring;

import com.forwardmeasure.jpa.identity.OwnedEntity;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface SpringOwnedRepository<
        T extends OwnedEntity<I>, I extends Serializable>
        extends SpringAuditedRepository<T, I> {

    List<T> findByOwnerId(Long ownerId);

    List<T> findByOwnerSubjectIdentifier(String subjectIdentifier);

    long countByOwnerId(Long ownerId);

    boolean existsByIdAndOwnerId(I id, Long ownerId);

    boolean existsByUuidAndOwnerId(UUID uuid, Long ownerId);

    boolean existsByIdAndOwnerSubjectIdentifier(
            I id, String subjectIdentifier);

    boolean existsByUuidAndOwnerSubjectIdentifier(
            UUID uuid, String subjectIdentifier);
}
