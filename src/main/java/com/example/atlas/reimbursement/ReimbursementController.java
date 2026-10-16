package com.example.atlas.reimbursement;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/expenses/{expenseId}/reimbursements")
public class ReimbursementController {
    private final ReimbursementService service;
    public ReimbursementController(ReimbursementService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ReimbursementResponse> record(@PathVariable UUID expenseId, @Valid @RequestBody ReimbursementRequest request) {
        var result = service.record(expenseId, request);
        return ResponseEntity.created(URI.create("/api/expenses/" + expenseId + "/reimbursements/" + result.getId())).body(ReimbursementResponse.from(result));
    }

    @GetMapping
    public List<ReimbursementResponse> list(@PathVariable UUID expenseId) { return service.list(expenseId).stream().map(ReimbursementResponse::from).toList(); }

    @GetMapping("/{id}")
    public ReimbursementResponse get(@PathVariable UUID expenseId, @PathVariable UUID id) {
        return service.list(expenseId).stream().filter(r -> r.getId().equals(id)).findFirst().map(ReimbursementResponse::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Reimbursement not found for this expense"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID expenseId, @PathVariable UUID id) { service.remove(expenseId, id); return ResponseEntity.noContent().build(); }

    public record ReimbursementResponse(UUID id, UUID expenseId, BigDecimal amount, String currency,
                                         LocalDate receivedOn, String note, Instant createdAt) {
        static ReimbursementResponse from(Reimbursement r) {
            return new ReimbursementResponse(r.getId(), r.getExpenseId(), r.getAmount(), r.getCurrency(), r.getReceivedOn(), r.getNote(), r.getCreatedAt());
        }
    }
}
