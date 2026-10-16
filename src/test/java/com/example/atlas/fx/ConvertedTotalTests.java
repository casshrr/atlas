package com.example.atlas.fx;

import com.example.atlas.expense.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/atlas_test}",
    "spring.datasource.username=${TEST_DB_USER:ernur}", "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@Transactional
class ConvertedTotalTests {
    @Autowired MockMvc mvc;
    @Autowired ExpenseRepository repository;
    @Autowired com.example.atlas.reimbursement.ReimbursementService reimbursements;
    @MockitoBean HistoricalRateProvider provider;

    private void expense(String amount, String currency, LocalDate date) {
        repository.save(new Expense(new BigDecimal(amount), currency, "Food", null, date));
    }

    @Test void convertsOnPurchaseDateCachesRatesAndKeepsOriginals() throws Exception {
        var day = LocalDate.of(2026, 9, 25);
        expense("50", "GBP", day); expense("15000", "KZT", day);
        when(provider.fetch("GBP", "KZT", day)).thenReturn(new HistoricalRateProvider.Rate(new BigDecimal("600"), day));
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/expenses/converted-total?currency=KZT"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(true))
                    .andExpect(jsonPath("$.amount").value(45000))
                    .andExpect(jsonPath("$.ratesUsed[0].purchaseDate").value(day.toString()));
        }
        verify(provider, times(1)).fetch("GBP", "KZT", day);
        mvc.perform(get("/api/expenses/totals")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("GBP")).andExpect(jsonPath("$[0].amount").value(50))
                .andExpect(jsonPath("$[1].currency").value("KZT")).andExpect(jsonPath("$[1].amount").value(15000));
    }

    @Test void missingRateNeverReturnsAPartialTotal() throws Exception {
        var day = LocalDate.of(2026, 9, 26);
        expense("50", "GBP", day); expense("15000", "KZT", day);
        when(provider.fetch("GBP", "KZT", day)).thenThrow(new java.io.IOException("offline"));
        mvc.perform(get("/api/expenses/converted-total?currency=KZT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.amount").isEmpty()).andExpect(jsonPath("$.missingRates.length()").value(1));
    }

    @Test void roundsPerExpenseAndFiltersBeforeConversion() throws Exception {
        var day = LocalDate.of(2026, 9, 27);
        expense("0.01", "GBP", day); expense("0.01", "GBP", day);
        expense("1000", "GBP", day.minusDays(1));
        when(provider.fetch("GBP", "KZT", day)).thenReturn(new HistoricalRateProvider.Rate(new BigDecimal("1.5"), day.minusDays(2)));
        mvc.perform(get("/api/expenses/converted-total?currency=KZT&from=" + day + "&to=" + day))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(0.04))
                .andExpect(jsonPath("$.ratesUsed[0].rateDate").value(day.minusDays(2).toString()));
        verify(provider, never()).fetch("GBP", "KZT", day.minusDays(1));
    }

    @Test void sameCurrencyAndEmptyResultsNeedNoProvider() throws Exception {
        mvc.perform(get("/api/expenses/converted-total?currency=GBP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(0));
        expense("50", "GBP", LocalDate.now());
        mvc.perform(get("/api/expenses/converted-total?currency=GBP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(50));
        verifyNoInteractions(provider);
        mvc.perform(get("/api/expenses/converted-total?currency=XYZ")).andExpect(status().isBadRequest());
    }

    @Test void convertsNetCostAndSkipsRatesForFullyReimbursedPurchases() throws Exception {
        var day = LocalDate.of(2026, 9, 25);
        var expense = repository.save(new Expense(new BigDecimal("100"), "GBP", "Food", null, day));
        reimbursements.record(expense.getId(), new com.example.atlas.reimbursement.ReimbursementRequest(new BigDecimal("40"), "GBP", day, null));
        when(provider.fetch("GBP", "KZT", day)).thenReturn(new HistoricalRateProvider.Rate(new BigDecimal("600"), day));
        mvc.perform(get("/api/expenses/converted-total?currency=KZT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(36000));
        reimbursements.record(expense.getId(), new com.example.atlas.reimbursement.ReimbursementRequest(new BigDecimal("60"), "GBP", day, null));
        reset(provider);
        mvc.perform(get("/api/expenses/converted-total?currency=KZT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(0));
        verifyNoInteractions(provider);
    }
}
