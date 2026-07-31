package com.forwardmeasure.jpa.core.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;

@FunctionalInterface
public interface JpaSpecification<T> extends Serializable {

    Predicate toPredicate(
            Root<T> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder);

    default JpaSpecification<T> and(JpaSpecification<T> other) {
        return (root, query, builder) -> builder.and(
                toPredicate(root, query, builder),
                other.toPredicate(root, query, builder));
    }

    default JpaSpecification<T> or(JpaSpecification<T> other) {
        return (root, query, builder) -> builder.or(
                toPredicate(root, query, builder),
                other.toPredicate(root, query, builder));
    }

    default JpaSpecification<T> not() {
        return (root, query, builder) ->
                builder.not(toPredicate(root, query, builder));
    }
}
