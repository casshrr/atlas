package com.example.atlas.reimbursement;

import com.example.atlas.expense.*;
import com.example.atlas.common.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ReimbursementService {
    private final ReimbursementRepository reimbursements;
    private final ExpenseRepository expenses;

    public ReimbursementService(ReimbursementRepository reimbursements, ExpenseRepository expenses) {
        this.reimbursements = reimbursements; this.expenses = expenses;
    }

    @Transactional
    public Reimbursement record(UUID expenseId, ReimbursementRequest request) {
        // Every reimbursement and expense mutation locks the same parent row first.
        var expense = expenses.findForUpdate(expenseId).orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        if (!expense.getCurrency().equals(request.currency())) throw new IllegalArgumentException("Reimbursements must use the expense currency: " + expense.getCurrency());
        if (request.receivedOn().isBefore(expense.getSpentOn())) throw new IllegalArgumentException("Received date cannot be before the purchase date");
        var totals = reimbursements.totalsFor(List.of(expenseId));
        BigDecimal refunded = totals.isEmpty() ? BigDecimal.ZERO : totals.get(0).getAmount();
        if (refunded.add(request.amount()).compareTo(expense.getAmount()) > 0)
            throw new ConflictException("Reimbursement exceeds the remaining cost of this expense");
        return reimbursements.save(new Reimbursement(expenseId, request.amount(), expense.getCurrency(), request.receivedOn(), request.note()));
    }

    @Transactional(readOnly = true)
    public List<Reimbursement> list(UUID expenseId) {
        if (!expenses.existsById(expenseId)) throw new ExpenseNotFoundException(expenseId);
        return reimbursements.findByExpenseIdOrderByReceivedOnDescCreatedAtDesc(expenseId);
    }

    @Transactional
    public void remove(UUID expenseId, UUID id) {
        expenses.findForUpdate(expenseId).orElseThrow(() -> new ExpenseNotFoundException(expenseId));
        var reimbursement = reimbursements.findByIdAndExpenseId(id, expenseId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Reimbursement not found for this expense"));
        reimbursements.delete(reimbursement);
    }
}
