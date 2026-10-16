package com.example.atlas;

import com.example.atlas.capture.*;
import com.example.atlas.expense.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/atlas_test}",
 "spring.datasource.username=${TEST_DB_USER:ernur}", "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@Transactional
class CaptureTests {
    @Autowired MockMvc mvc;
    @Autowired CaptureService captures;
    @Autowired CaptureRepository repository;
    @Autowired ExpenseRepository expenses;
    @Autowired NotificationParser parser;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    private CaptureRequest request(UUID id, String text, String merchant, Instant time) {
        return new CaptureRequest(id, "Test bank " + getClass().getSimpleName(), time, "Europe/London", text, null, null, merchant, null);
    }
    private CaptureRequest request(String text, String merchant) { return request(UUID.randomUUID(), text, merchant, Instant.parse("2026-09-14T12:00:00Z")); }
    private CapturedPayment capture(CaptureRequest input) throws Exception {
        var response = mvc.perform(post("/api/captures").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return repository.findById(UUID.fromString(json.readTree(response.getContentAsString()).path("id").asText())).orElseThrow();
    }
    private void approve(CapturedPayment payment, boolean duplicate) throws Exception {
        var expense = new ExpenseRequest(payment.getAmount(), payment.getCurrency(), "Food", payment.getMerchant(), payment.getSpentOn());
        var approval = new CaptureService.Approval(expense, payment.getMerchant(), true, duplicate);
        mvc.perform(post("/api/captures/" + payment.getId() + "/approve").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(approval))).andExpect(status().isOk());
    }

    @Test void reviewsFirstMerchantThenLearnsAndAutomaticallyImportsClearPurchases() throws Exception {
        long before = expenses.count();
        var first = capture(request("Paid £12.50 at Example Cafe", null));
        assertEquals("Example Cafe", first.getMerchant()); assertEquals("REVIEW", first.getStatus()); assertEquals(before, expenses.count());
        approve(first, false);
        var second = capture(request(UUID.randomUUID(), "Paid £14.50 at Example Cafe", null, Instant.parse("2026-09-15T12:00:00Z")));
        assertEquals("IMPORTED", second.getStatus()); assertEquals("Food", second.getCategory()); assertEquals(before + 2, expenses.count());
        assertNotNull(second.getExpenseId());
    }

    @Test void deliveryAndApprovalRetriesNeverCreateAnotherExpense() throws Exception {
        var input = request("Paid GBP 17.50", "Retry Cafe");
        var first = capture(input); approve(first, false);
        long before = expenses.count();
        assertEquals(first.getId(), capture(input).getId());
        assertEquals(first.getId(), capture(request(UUID.randomUUID(), input.text(), input.merchant(), input.capturedAt())).getId());
        approve(first, false); assertEquals(before, expenses.count());
        var changed = request(input.eventId(), "Paid GBP 99.50", input.merchant(), input.capturedAt());
        mvc.perform(post("/api/captures").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed))).andExpect(status().isConflict());
    }

    @Test void suspiciousFormatsAndNonPurchasesAlwaysNeedReview() throws Exception {
        var first = capture(request("Paid £2.50", "Safe Cafe")); approve(first, false);
        for (String text : List.of("Paid $12.50", "Paid £1,234", "Paid GBP 1 234,50", "Paid £12.50; balance £500.00",
                "Payment declined £8.00", "Refund received £8.00", "Pending payment £8.00", "Transfer £8.00", "Paid -£8.00", "Paid £-8.00", "Paid something")) {
            var pending = capture(request(text, "Safe Cafe"));
            assertEquals("REVIEW", pending.getStatus(), text); assertNull(pending.getExpenseId());
        }
    }

    @Test void possibleCrossSourceAndManualDuplicatesNeedExplicitOverride() throws Exception {
        var first = capture(request("Paid GBP 55.25", "Duplicate Cafe")); approve(first, false);
        var second = capture(request(UUID.randomUUID(), "Paid GBP 55.25 at Duplicate Cafe", "Duplicate Cafe", Instant.parse("2026-09-14T12:01:00Z")));
        assertEquals("REVIEW", second.getStatus()); assertTrue(second.getReason().contains("Possible duplicate"));
        var approval = new CaptureService.Approval(new ExpenseRequest(second.getAmount(), "GBP", "Food", "Checked", second.getSpentOn()), "Duplicate Cafe", false, false);
        mvc.perform(post("/api/captures/" + second.getId() + "/approve").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(approval))).andExpect(status().isConflict());
        approve(second, true); assertEquals("IMPORTED", repository.findById(second.getId()).orElseThrow().getStatus());
    }

    @Test void dismissalStaysDismissedAndDoesNotAffectTotals() throws Exception {
        long before = expenses.count(); var input = request("Paid £9.50", "Dismiss Cafe"); var payment = capture(input);
        mvc.perform(post("/api/captures/" + payment.getId() + "/dismiss")).andExpect(status().isOk());
        assertEquals("DISMISSED", capture(input).getStatus()); assertEquals(before, expenses.count());
        mvc.perform(get("/api/captures")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void captureUsesPhoneTimeZoneRatherThanMacArrivalTime() throws Exception {
        var input = new CaptureRequest(UUID.randomUUID(), "Zone bank", Instant.parse("2026-09-14T23:30:00Z"), "Asia/Almaty", "Paid ₸1500", null, null, "Zone Cafe", null);
        var payment = capture(input); assertEquals(LocalDate.of(2026, 9, 15), payment.getSpentOn()); assertEquals("KZT", payment.getCurrency());
    }

    @Test void inputAndApprovalValidationProtectTheImportBoundary() throws Exception {
        for (String body : List.of("{}", "{\"eventId\":\"bad\"}", json.writeValueAsString(new CaptureRequest(UUID.randomUUID(), "Bank", Instant.now().minusSeconds(10), "bad/zone", "", null, null, null, null)),
                json.writeValueAsString(new CaptureRequest(UUID.randomUUID(), "Bank", Instant.now().minusSeconds(10), "UTC", "a".repeat(4001), null, null, null, null)))) {
            mvc.perform(post("/api/captures").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        var pending = capture(request("Paid £12.34", "Validation Cafe"));
        var invalid = new CaptureService.Approval(new ExpenseRequest(new BigDecimal("-1"), "XYZ", "Unknown", "", LocalDate.now().plusDays(1)), "", true, false);
        mvc.perform(post("/api/captures/" + pending.getId() + "/approve").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
        assertEquals("REVIEW", repository.findById(pending.getId()).orElseThrow().getStatus());
    }

    @Test void explicitFieldsCannotOverrideConflictingNotificationEvidence() {
        var request = new CaptureRequest(UUID.randomUUID(), "Bank", Instant.now(), "UTC", "Paid £12.50", new BigDecimal("99.00"), "EUR", "Cafe", "purchase");
        var result = parser.parse(request); assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("Amount conflicts")));
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("Currency conflicts")));
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDeliveryAndApprovalAreIdempotent() throws Exception {
        UUID id = UUID.randomUUID();
        var input = new CaptureRequest(id, "Concurrent " + id, Instant.parse("2026-09-12T12:00:00Z"), "UTC", "Paid £18.73", null, null, "Concurrent Cafe", null);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var deliveries = pool.invokeAll(List.<java.util.concurrent.Callable<CapturedPayment>>of(() -> captures.capture(input), () -> captures.capture(input)));
            assertEquals(deliveries.get(0).get().getId(), deliveries.get(1).get().getId());
            var approval = new CaptureService.Approval(new ExpenseRequest(new BigDecimal("18.73"), "GBP", "Food", "Concurrent", LocalDate.of(2026, 9, 12)), "Concurrent Cafe", false, true);
            var results = pool.invokeAll(List.<java.util.concurrent.Callable<CapturedPayment>>of(() -> captures.approve(id, approval), () -> captures.approve(id, approval)));
            assertEquals(results.get(0).get().getExpenseId(), results.get(1).get().getExpenseId());
            assertEquals("", results.get(0).get().getRawText());
        } finally {
            pool.shutdownNow();
            var created = jdbc.queryForList("SELECT expense_id FROM captured_payments WHERE id = ?", UUID.class, id);
            jdbc.update("DELETE FROM captured_payments WHERE id = ?", id);
            created.stream().filter(Objects::nonNull).forEach(expenseId -> jdbc.update("DELETE FROM expenses WHERE id = ?", expenseId));
        }
    }
}
