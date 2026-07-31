package com.forwardmeasure.jpa.contract;

import com.forwardmeasure.jpa.identity.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Concrete entity used by every provider adapter's compatibility suite.
 */
@Entity
@Table(name = "jpa_contract_owned_entity")
@SequenceGenerator(
        name = "jpa_contract_owned_entity_generator",
        sequenceName = "jpa_contract_owned_entity_id_seq",
        allocationSize = 1)
public class ContractOwnedEntity extends OwnedEntity<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(
            generator = "jpa_contract_owned_entity_generator",
            strategy = GenerationType.SEQUENCE)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
