package com.example.atlas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://127.0.0.1:55439/atlas_test}",
 "spring.datasource.username=${TEST_DB_USER:ernur}", "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureMockMvc
@Transactional
class AtlasApplicationTests {
    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    private String body(String amount, String currency, String category) {
        return """
          {"amount":%s,"currency":"%s","category":"%s","description":"Lunch","spentOn":"%s"}
          """.formatted(amount, currency, category, LocalDate.now().minusDays(1));
    }
    private String create(String amount, String currency, String category) throws Exception {
        return mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body(amount, currency, category))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
    }
    @Test void createReadReplaceDelete() throws Exception {
        String id = json.readTree(create("12.50", "kzt", "Food")).path("id").asText();
        mvc.perform(get("/api/expenses/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("KZT")).andExpect(jsonPath("$.amount").value(12.5));
        mvc.perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body("21.75", "USD", "Travel"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(21.75));
        mvc.perform(delete("/api/expenses/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses/" + id)).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidMoney() throws Exception {
        for (String amount : new String[]{"0", "-5", "1.123", "100000000000000000.00"})
            mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                    .content(body(amount, "KZT", "Food"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray());
    }
    @Test void rejectsMissingFieldsFutureDateAndBlankCategory() throws Exception {
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body("1", "KZT", "   "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body("1", "KZT", "Food").replace(LocalDate.now().minusDays(1).toString(),
                    LocalDate.now().plusDays(1).toString()))).andExpect(status().isBadRequest());
    }
    @Test void filterPaginationAndCurrencyTotals() throws Exception {
        create("10.25", "KZT", "Food"); create("2.50", "KZT", "Food");
        create("3.00", "USD", "Food"); create("20", "USD", "Travel");
        mvc.perform(get("/api/expenses?category=food&size=2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/expenses/totals?category=food")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("KZT")).andExpect(jsonPath("$[0].amount").value(12.75))
                .andExpect(jsonPath("$[1].currency").value("USD")).andExpect(jsonPath("$[1].amount").value(3));
        mvc.perform(get("/api/expenses?from=" + LocalDate.now())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void rejectsInvalidQueriesAndMalformedJson() throws Exception {
        for (String query : new String[]{"size=101", "size=0", "page=-1", "from=bad", "from=2026-02-02&to=2026-01-01"})
            mvc.perform(get("/api/expenses?" + query)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }
    @Test void missingUpdateAndDeleteReturn404() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(put("/api/expenses/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body("1", "KZT", "Food"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/expenses/" + id)).andExpect(status().isNotFound());
    }

    @Test void enforcesSupportedCategoriesAndCurrencies() throws Exception {
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body("1", "GBP", "Foood"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body("1", "XYZ", "Food"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(body("1", "gbp", " food "))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("Food")).andExpect(jsonPath("$.currency").value("GBP"));
    }

}
