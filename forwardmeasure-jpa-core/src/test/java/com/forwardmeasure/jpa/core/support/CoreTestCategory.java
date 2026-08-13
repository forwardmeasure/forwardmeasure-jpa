package com.forwardmeasure.jpa.core.support;

import com.forwardmeasure.jpa.core.entity.AbstractBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "core_test_category")
@SequenceGenerator(
        name = "core_test_category_generator",
        sequenceName = "core_test_category_seq",
        allocationSize = 1)
@Getter
@Setter
@NoArgsConstructor
public class CoreTestCategory extends AbstractBaseEntity<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(
            generator = "core_test_category_generator",
            strategy = GenerationType.SEQUENCE)
    @Column(name = "category_key")
    private Long categoryKey;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Override
    public Long getId() {
        return categoryKey;
    }

    @Override
    public void setId(Long id) {
        categoryKey = id;
    }
}
