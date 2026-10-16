package com.example.atlas.expense;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import com.example.atlas.reimbursement.ReimbursementRepository;
import com.example.atlas.common.ConflictException;
import org.springframework.transaction.annotation.Isolation;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ReimbursementRepository reimbursements;

    public ExpenseService(ExpenseRepository expenseRepository, ReimbursementRepository reimbursements){
        this.expenseRepository = expenseRepository;
        this.reimbursements = reimbursements;
    }

    @Transactional
    public ExpenseResponse createExpense(
            BigDecimal amount,
            String currency,
            String category,
            String description,
            LocalDate spentOn
    ){
        Expense newExpense = new Expense(amount, currency, category, description, spentOn);

        return ExpenseResponse.from(expenseRepository.save(newExpense), BigDecimal.ZERO);

    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExpenseResponse getExpenseById(UUID id){
        return responses(List.of(expenseRepository.findById(id).orElseThrow(() -> new ExpenseNotFoundException(id)))).get(0);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ExpenseResponse> list(int page, int size, String category, LocalDate from, LocalDate to) {
        var result = expenseRepository.findAll(ExpenseFilters.matching(category, from, to),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("spentOn"), Sort.Order.asc("id"))));
        return new org.springframework.data.domain.PageImpl<>(responses(result.getContent()), result.getPageable(), result.getTotalElements());
    }

    @Transactional
    public ExpenseResponse update(UUID id, ExpenseRequest request) {
        Expense expense = expenseRepository.findForUpdate(id).orElseThrow(() -> new ExpenseNotFoundException(id));
        var recorded = reimbursements.findByExpenseIdOrderByReceivedOnDescCreatedAtDesc(id);
        var reimbursed = recorded.stream().map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.amount().compareTo(reimbursed) < 0) throw new ConflictException("Amount cannot be less than reimbursements already received");
        if (!recorded.isEmpty() && !expense.getCurrency().equals(request.currency())) throw new ConflictException("Remove reimbursements before changing the expense currency");
        if (recorded.stream().anyMatch(r -> r.getReceivedOn().isBefore(request.spentOn()))) throw new ConflictException("Purchase date cannot be after an existing reimbursement");
        expense.setAmount(request.amount());
        expense.setCurrency(request.currency());
        expense.setCategory(request.category());
        expense.setDescription(request.description());
        expense.setSpentOn(request.spentOn());
        return ExpenseResponse.from(expense, reimbursed);
    }

    @Transactional
    public void delete(UUID id) {
        var expense = expenseRepository.findForUpdate(id).orElseThrow(() -> new ExpenseNotFoundException(id));
        if (reimbursements.existsByExpenseId(id)) throw new ConflictException("Remove reimbursements before deleting this expense");
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<CurrencyTotal> totals(String category, LocalDate from, LocalDate to) {
        var rows = reportingRows(category, from, to);
        Map<String, BigDecimal> gross = new TreeMap<>();
        Map<String, BigDecimal> refunded = new TreeMap<>();
        rows.forEach(e -> { gross.merge(e.currency(), e.amount(), BigDecimal::add); refunded.merge(e.currency(), e.reimbursedAmount(), BigDecimal::add); });
        return gross.entrySet().stream().map(e -> new CurrencyTotal(e.getKey(), e.getValue().subtract(refunded.get(e.getKey())), e.getValue(), refunded.get(e.getKey()))).toList();
    }

    public record CurrencyTotal(String currency, BigDecimal amount, BigDecimal grossAmount, BigDecimal reimbursedAmount) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ConversionInput> conversionInputs(String category, LocalDate from, LocalDate to) {
        return reportingRows(category, from, to).stream()
                .map(e -> new ConversionInput(e.netAmount(), e.currency(), e.spentOn())).toList();
    }

    public record ConversionInput(BigDecimal amount, String currency, LocalDate date) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ExpenseResponse> reportingRows(String category, LocalDate from, LocalDate to) {
        return responses(expenseRepository.findAll(ExpenseFilters.matching(category, from, to)));
    }

    private List<ExpenseResponse> responses(List<Expense> expenses) {
        if (expenses.isEmpty()) return List.of();
        var totals = new HashMap<UUID, BigDecimal>();
        reimbursements.totalsFor(expenses.stream().map(Expense::getId).toList())
                .forEach(row -> totals.put(row.getExpenseId(), row.getAmount()));
        return expenses.stream().map(e -> ExpenseResponse.from(e, totals.getOrDefault(e.getId(), BigDecimal.ZERO))).toList();
    }


}
