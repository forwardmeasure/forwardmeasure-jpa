package com.forwardmeasure.jpa.contract.entity;

import com.forwardmeasure.jpa.identity.entity.OwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Concrete entity used by every provider adapter's compatibility suite. */
@Entity
@Table(name = "jpa_contract_owned_entity")
@SequenceGenerator(
    name = "jpa_contract_owned_entity_generator",
    sequenceName = "jpa_contract_owned_entity_id_seq",
    allocationSize = 1)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
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
}
