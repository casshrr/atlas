package com.example.atlas.expense;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseService service;

    public ExpenseController(ExpenseService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        var expense = service.createExpense(request.amount(), request.currency(), request.category(),
                request.description(), request.spentOn());
        return ResponseEntity.created(URI.create("/api/expenses/" + expense.id())).body(expense);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable UUID id) {
        return service.getExpenseById(id);
    }

    @GetMapping
    public ExpensePage list(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) LocalDate from,
                            @RequestParam(required = false) LocalDate to) {
        validateDates(from, to);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be >= 0; size must be between 1 and 100");
        var result = service.list(page, size, category, from, to);
        return new ExpensePage(result.getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/totals")
    public List<ExpenseService.CurrencyTotal> totals(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) LocalDate from,
                                                    @RequestParam(required = false) LocalDate to) {
        validateDates(from, to);
        return service.totals(category, from, to);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void validateDates(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("From date must not be after to date");
    }

    public record ExpensePage(List<ExpenseResponse> content, int page, int size, long totalElements, int totalPages) {}
}
