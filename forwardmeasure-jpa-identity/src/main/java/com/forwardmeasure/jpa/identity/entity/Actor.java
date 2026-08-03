package com.forwardmeasure.jpa.identity.entity;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

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
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Actor extends AbstractBaseEntity<Long> {

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

}
