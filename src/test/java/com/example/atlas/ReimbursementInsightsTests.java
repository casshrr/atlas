package com.example.atlas;

import com.example.atlas.expense.*;
import com.example.atlas.reimbursement.*;
import com.example.atlas.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/atlas_test}",
 "spring.datasource.username=${TEST_DB_USER:ernur}", "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@Transactional
class ReimbursementInsightsTests {
    @Autowired MockMvc mvc;
    @Autowired ReimbursementService reimbursements;
    @Autowired ExpenseService expenses;
    private final JsonMapper json = JsonMapper.builder().build();

    private String expenseBody(String amount, String currency, String category, String day) {
        return """
          {"amount":%s,"currency":"%s","category":"%s","spentOn":"%s"}
          """.formatted(amount, currency, category, day);
    }
    private String create(String amount, String currency, String category, String day) throws Exception {
        var response = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody(amount, currency, category, day))).andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("id").asText();
    }
    private String reimbursementBody(String amount, String currency, String date) {
        return """
          {"amount":%s,"currency":"%s","receivedOn":"%s","note":"Dinner repayment"}
          """.formatted(amount, currency, date);
    }
    private String repay(String id, String amount, String currency, String date) throws Exception {
        return json.readTree(mvc.perform(post("/api/expenses/" + id + "/reimbursements").contentType(MediaType.APPLICATION_JSON)
                .content(reimbursementBody(amount, currency, date))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString()).path("id").asText();
    }

    @Test void partialMultipleAndFullReimbursementPreserveTheOriginal() throws Exception {
        String id = create("100", "GBP", "Food", "2026-09-20");
        String first = repay(id, "40", "GBP", "2026-10-01");
        mvc.perform(get("/api/expenses/" + id)).andExpect(jsonPath("$.amount").value(100))
                .andExpect(jsonPath("$.reimbursedAmount").value(40)).andExpect(jsonPath("$.netAmount").value(60));
        repay(id, "60", "GBP", "2026-10-02");
        mvc.perform(get("/api/expenses/" + id)).andExpect(jsonPath("$.netAmount").value(0));
        mvc.perform(get("/api/expenses/" + id + "/reimbursements")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/expenses/" + id + "/reimbursements/" + first)).andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses/" + id)).andExpect(jsonPath("$.netAmount").value(40));
    }

    @Test void rejectsWrongCurrencyOverpaymentAndUnreceivedMoney() throws Exception {
        String id = create("100", "GBP", "Food", "2026-09-20");
        for (String body : List.of(reimbursementBody("10", "KZT", "2026-10-01"), reimbursementBody("10", "GBP", "2026-09-19"),
                reimbursementBody("10", "GBP", LocalDate.now().plusDays(1).toString()), reimbursementBody("0", "GBP", "2026-10-01"),
                reimbursementBody("1.234", "GBP", "2026-10-01"))) {
            mvc.perform(post("/api/expenses/" + id + "/reimbursements").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        repay(id, "60", "GBP", "2026-10-01");
        mvc.perform(post("/api/expenses/" + id + "/reimbursements").contentType(MediaType.APPLICATION_JSON)
                .content(reimbursementBody("50", "GBP", "2026-10-01"))).andExpect(status().isConflict());
    }

    @Test void protectsLedgerFromIncompatibleEditsAndCrossExpenseDeletion() throws Exception {
        String id = create("100", "GBP", "Food", "2026-09-20");
        String reimbursement = repay(id, "40", "GBP", "2026-10-01");
        for (String body : List.of(expenseBody("30", "GBP", "Food", "2026-09-20"), expenseBody("100", "KZT", "Food", "2026-09-20"),
                expenseBody("100", "GBP", "Food", "2026-10-02"))) {
            mvc.perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        }
        mvc.perform(delete("/api/expenses/" + id)).andExpect(status().isConflict());
        String other = create("100", "GBP", "Food", "2026-09-20");
        mvc.perform(delete("/api/expenses/" + other + "/reimbursements/" + reimbursement)).andExpect(status().isNotFound());
        mvc.perform(get("/api/expenses/" + id + "/reimbursements/" + reimbursement)).andExpect(status().isOk());
    }

    @Test void reportsNetSpendingInThePurchasePeriodAndKeepsCurrenciesSeparate() throws Exception {
        String id = create("100", "GBP", "Food", "2026-09-20");
        repay(id, "40", "GBP", "2026-10-01");
        create("40", "GBP", "Transport", "2026-09-22");
        create("20", "GBP", "Food", "2026-08-10");
        create("10000", "KZT", "Food", "2026-09-21");
        mvc.perform(get("/api/expenses/totals?from=2026-09-01&to=2026-09-30"))
                .andExpect(jsonPath("$[0].currency").value("GBP")).andExpect(jsonPath("$[0].grossAmount").value(140))
                .andExpect(jsonPath("$[0].reimbursedAmount").value(40)).andExpect(jsonPath("$[0].amount").value(100));
        mvc.perform(get("/api/expenses/totals?from=2026-10-01&to=2026-10-15"))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/insights/spending?from=2026-09-01&to=2026-09-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.previousFrom").value("2026-08-02"))
                .andExpect(jsonPath("$.currencies.length()").value(2))
                .andExpect(jsonPath("$.currencies[0].netAmount").value(100))
                .andExpect(jsonPath("$.currencies[0].dailyAverage").value(3.33))
                .andExpect(jsonPath("$.currencies[0].previousNetAmount").value(20))
                .andExpect(jsonPath("$.currencies[0].changePercent").value(400))
                .andExpect(jsonPath("$.currencies[0].categories[0].category").value("Food"))
                .andExpect(jsonPath("$.currencies[0].categories[0].sharePercent").value(60));
    }

    @Test void emptyZeroBaselineAndInvalidInsightIntervals() throws Exception {
        mvc.perform(get("/api/insights/spending?from=2026-09-01&to=2026-09-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currencies.length()").value(0));
        create("10", "GBP", "Food", "2026-09-01");
        mvc.perform(get("/api/insights/spending?from=2026-09-01&to=2026-09-30"))
                .andExpect(jsonPath("$.currencies[0].changePercent").isEmpty())
                .andExpect(jsonPath("$.currencies[0].trend.length()").value(30))
                .andExpect(jsonPath("$.currencies[0].trend[1].amount").value(0));
        for (String query : List.of("from=2026-09-30&to=2026-09-01", "from=2020-01-01&to=2026-01-01", "to=" + LocalDate.now().plusDays(1)))
            mvc.perform(get("/api/insights/spending?" + query)).andExpect(status().isBadRequest());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRepaymentsCannotExceedTheExpense() throws Exception {
        UUID id = UUID.fromString(create("100", "GBP", "Other", LocalDate.now().toString()));
        var pool = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        try {
            Callable<Boolean> record = () -> {
                gate.await();
                try { reimbursements.record(id, new ReimbursementRequest(new BigDecimal("60"), "GBP", LocalDate.now(), null)); return true; }
                catch (ConflictException expected) { return false; }
            };
            var first = pool.submit(record); var second = pool.submit(record); gate.countDown();
            int succeeded = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, succeeded);
            assertEquals(0, expenses.getExpenseById(id).netAmount().compareTo(new BigDecimal("40")));
        } finally {
            pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS);
            for (var row : reimbursements.list(id)) reimbursements.remove(id, row.getId());
            expenses.delete(id);
        }
    }

    @Test void monthlyTrendKeepsEmptyMonthsAndFullRepaymentHasZeroShare() throws Exception {
        String id = create("100", "GBP", "Food", "2026-01-15");
        repay(id, "100", "GBP", "2026-02-01");
        mvc.perform(get("/api/insights/spending?from=2026-01-01&to=2026-03-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currencies[0].netAmount").value(0))
                .andExpect(jsonPath("$.currencies[0].categories[0].sharePercent").value(0))
                .andExpect(jsonPath("$.currencies[0].trend.length()").value(3))
                .andExpect(jsonPath("$.currencies[0].trend[1].label").value("Feb 2026"))
                .andExpect(jsonPath("$.currencies[0].trend[1].amount").value(0));
    }
}
