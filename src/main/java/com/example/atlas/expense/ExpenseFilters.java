package com.example.atlas.expense;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;

final class ExpenseFilters {
    private ExpenseFilters() {}

    static Specification<Expense> matching(String category, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.strip().toLowerCase(java.util.Locale.ROOT)));
            }
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("spentOn"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("spentOn"), to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
