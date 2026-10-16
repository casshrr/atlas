package com.example.atlas.capture;

import com.example.atlas.common.ConflictException;
import com.example.atlas.expense.ExpenseRepository;
import com.example.atlas.expense.ExpenseRequest;
import com.example.atlas.expense.ExpenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Coordinates delivery retries, review decisions and atomic expense creation. */
@Service
public class CaptureService {
    private static final String REVIEW = "REVIEW";
    private static final String IMPORTED = "IMPORTED";
    private static final String DISMISSED = "DISMISSED";

    private final CaptureRepository captures;
    private final NotificationParser parser;
    private final ExpenseService expenses;
    private final ExpenseRepository expenseRepository;
    private final MerchantCategoryRepository merchantCategories;
    private final JdbcTemplate jdbc;

    public CaptureService(CaptureRepository captures, NotificationParser parser, ExpenseService expenses,
                          ExpenseRepository expenseRepository, MerchantCategoryRepository merchantCategories,
                          JdbcTemplate jdbc) {
        this.captures = captures;
        this.parser = parser;
        this.expenses = expenses;
        this.expenseRepository = expenseRepository;
        this.merchantCategories = merchantCategories;
        this.jdbc = jdbc;
    }

    @Transactional
    public CapturedPayment capture(CaptureRequest request) {
        lockCaptureTransactions();
        ZoneId zone = parseTimeZone(request.timeZone());
        String fingerprint = CaptureFingerprint.of(request);
        var previousDelivery = findPreviousDelivery(request.eventId(), fingerprint);
        if (previousDelivery.isPresent()) {
            return previousDelivery.get();
        }

        var parsed = parser.parse(request);
        var payment = newPayment(request, parsed, zone, fingerprint);
        var reviewReasons = reviewReasons(payment, parsed);
        payment.setStatus(REVIEW);
        payment.setReason(String.join("; ", reviewReasons));

        if (reviewReasons.isEmpty()) {
            createExpense(payment, payment.getMerchant(), "Imported using your saved merchant category");
        }
        return captures.saveAndFlush(payment);
    }

    @Transactional(readOnly = true)
    public List<CapturedPayment> inbox() {
        return captures.findByStatusOrderByCreatedAtDesc(REVIEW, PageRequest.of(0, 100));
    }

    @Transactional
    public CapturedPayment approve(UUID id, Approval request) {
        lockCaptureTransactions();
        var payment = requireCapture(id);
        if (IMPORTED.equals(payment.getStatus())) {
            return payment; // Repeating an approval must not create another expense.
        }
        if (!REVIEW.equals(payment.getStatus())) {
            throw new ConflictException("This capture was dismissed");
        }

        var expense = request.expense();
        if (!request.allowPossibleDuplicate()
                && possibleDuplicate(expense.amount(), expense.currency(), expense.spentOn())) {
            throw new ConflictException("A matching expense exists. Check it first, then explicitly "
                    + "allow a separate purchase if correct");
        }

        applyApprovedFields(payment, request);
        createExpense(payment, expense.description(), "Confirmed by you");
        if (request.rememberCategory()) {
            merchantCategories.remember(payment.getSource(), payment.getMerchant(), expense.category());
        }
        return captures.saveAndFlush(payment);
    }

    @Transactional
    public CapturedPayment dismiss(UUID id) {
        lockCaptureTransactions();
        var payment = requireCapture(id);
        if (IMPORTED.equals(payment.getStatus())) {
            throw new ConflictException("This capture already created an expense; edit that expense instead");
        }
        payment.setStatus(DISMISSED);
        payment.setReason("Dismissed by you");
        payment.setRawText("");
        return captures.saveAndFlush(payment);
    }

    private Optional<CapturedPayment> findPreviousDelivery(UUID eventId, String fingerprint) {
        var previous = captures.findById(eventId);
        if (previous.isPresent()) {
            if (!previous.get().getPayloadHash().equals(fingerprint)) {
                throw new ConflictException("This event ID was already used for different content");
            }
            return previous;
        }
        return captures.findFirstByPayloadHashOrderByCreatedAtAsc(fingerprint);
    }

    private CapturedPayment newPayment(CaptureRequest request, NotificationParser.Parsed parsed,
                                       ZoneId zone, String fingerprint) {
        var payment = new CapturedPayment();
        payment.setId(request.eventId());
        payment.setPayloadHash(fingerprint);
        payment.setSource(request.source().strip());
        payment.setCapturedAt(request.capturedAt());
        payment.setTimeZone(zone.getId());
        payment.setRawText(request.text());
        payment.setAmount(parsed.amount());
        payment.setCurrency(parsed.currency());
        payment.setMerchant(parsed.merchant());
        payment.setSpentOn(request.capturedAt().atZone(zone).toLocalDate());
        payment.setCreatedAt(Instant.now());
        return payment;
    }

    private List<String> reviewReasons(CapturedPayment payment, NotificationParser.Parsed parsed) {
        var reasons = new ArrayList<>(parsed.reasons());
        if (payment.getSpentOn().isAfter(LocalDate.now())) {
            reasons.add("Confirm the local purchase date");
        }
        if (payment.getMerchant() != null) {
            merchantCategories.findCategory(payment.getSource(), payment.getMerchant())
                    .ifPresent(payment::setCategory);
        }
        if (payment.getCategory() == null) {
            reasons.add("Choose a category for this merchant");
        }
        if (possibleDuplicate(payment.getAmount(), payment.getCurrency(), payment.getSpentOn())) {
            reasons.add("Possible duplicate: an expense with this amount, currency and date already exists");
        }
        return reasons;
    }

    private void applyApprovedFields(CapturedPayment payment, Approval request) {
        var expense = request.expense();
        payment.setAmount(expense.amount());
        payment.setCurrency(expense.currency());
        payment.setCategory(expense.category());
        payment.setMerchant(request.merchant().strip());
        payment.setSpentOn(expense.spentOn());
    }

    private void createExpense(CapturedPayment payment, String description, String reason) {
        var saved = expenses.createExpense(payment.getAmount(), payment.getCurrency(),
                payment.getCategory(), description, payment.getSpentOn());
        payment.setExpenseId(saved.id());
        payment.setStatus(IMPORTED);
        payment.setReason(reason);
        payment.setRawText(""); // Retain the retry fingerprint, not the resolved notification body.
    }

    private boolean possibleDuplicate(BigDecimal amount, String currency, LocalDate date) {
        return amount != null && currency != null
                && expenseRepository.existsByAmountAndCurrencyAndSpentOn(amount, currency, date);
    }

    private CapturedPayment requireCapture(UUID id) {
        return captures.findById(id).orElseThrow(() -> new IllegalArgumentException("Capture not found"));
    }

    private ZoneId parseTimeZone(String name) {
        try {
            return ZoneId.of(name);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Use an IANA time zone, for example Europe/London");
        }
    }

    private void lockCaptureTransactions() {
        // Keep this lock inside the service transaction: it serializes capture and approval together.
        jdbc.execute("LOCK TABLE captured_payments IN SHARE ROW EXCLUSIVE MODE");
    }

    public record Approval(
            @NotNull @Valid ExpenseRequest expense,
            @NotBlank @Size(max = 200) String merchant,
            boolean rememberCategory,
            boolean allowPossibleDuplicate) {}
}
