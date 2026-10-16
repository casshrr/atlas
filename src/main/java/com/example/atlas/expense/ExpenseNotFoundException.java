package com.example.atlas.expense;

import java.util.UUID;

public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(UUID id) {
        super("Expense " + id + " was not found");
    }
}
