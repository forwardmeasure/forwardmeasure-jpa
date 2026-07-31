package com.forwardmeasure.jpa.identity;

import com.forwardmeasure.jpa.core.AbstractBaseEntity;
import com.forwardmeasure.jpa.core.AuthorizableResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Root identity record. An actor is intentionally not an {@code AuditedEntity}
 * and is never owned by another actor.
 */
@Entity
@Table(
        name = "actor",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_actor_identity_provider_subject_identifier",
                columnNames = {"identity_provider", "subject_identifier"}))
@SequenceGenerator(
        name = "actor_id_generator",
        sequenceName = "actor_id_seq",
        allocationSize = 1)
public class Actor extends AbstractBaseEntity<Long>
        implements AuthorizableResource {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(
            generator = "actor_id_generator",
            strategy = GenerationType.SEQUENCE)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "uuid", nullable = false, updatable = false, unique = true)
    private UUID uuid;

    @NotNull
    @Column(name = "subject_identifier", nullable = false)
    private String subjectIdentifier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false)
    private IdentityType type;

    @Column(name = "email")
    private String email;

    @Column(name = "identity_provider")
    private String identityProvider;

    @PrePersist
    protected void initializeIdentity() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }

    @Override
    public String getResourceId() {
        return uuid == null ? null : uuid.toString();
    }

    @Override
    public String getResourceType() {
        return Actor.class.getSimpleName();
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getSubjectIdentifier() {
        return subjectIdentifier;
    }

    public void setSubjectIdentifier(String subjectIdentifier) {
        this.subjectIdentifier = subjectIdentifier;
    }

    public IdentityType getType() {
        return type;
    }

    public void setType(IdentityType type) {
        this.type = type;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIdentityProvider() {
        return identityProvider;
    }

    public void setIdentityProvider(String identityProvider) {
        this.identityProvider = identityProvider;
    }
}
