package com.forwardmeasure.jpa.core.support;

import com.forwardmeasure.jpa.core.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "core_test_entity")
@SequenceGenerator(
        name = "core_test_entity_generator",
        sequenceName = "core_test_entity_seq",
        allocationSize = 1)
@Getter
@Setter
@NoArgsConstructor
public class CoreTestEntity extends AuditedEntity<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(
            generator = "core_test_entity_generator",
            strategy = GenerationType.SEQUENCE)
    @Column(name = "entity_key")
    private Long databaseKey;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_key")
    private CoreTestCategory category;

    @Override
    public Long getId() {
        return databaseKey;
    }

    @Override
    public void setId(Long id) {
        databaseKey = id;
    }
}
