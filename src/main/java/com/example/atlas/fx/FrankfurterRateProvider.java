package com.example.atlas.fx;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;

@Component
public class FrankfurterRateProvider implements HistoricalRateProvider {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final JsonMapper json = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    public FrankfurterRateProvider(@Value("${atlas.fx.base-url:https://api.frankfurter.dev/v2/}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override public Rate fetch(String from, String to, LocalDate requested) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "rate/" + from + "/" + to + "?date=" + requested))
                .timeout(Duration.ofSeconds(3)).header("Accept", "application/json").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Historical rate unavailable");
        var row = json.readTree(response.body());
        var date = LocalDate.parse(row.path("date").asText());
        var rate = new java.math.BigDecimal(row.path("rate").asText());
        // Reject malformed responses and never use a rate from after the purchase date.
        if (!row.path("base").asText().equalsIgnoreCase(from) || !row.path("quote").asText().equalsIgnoreCase(to)
                || rate.signum() <= 0 || date.isAfter(requested) || date.isBefore(requested.minusDays(7))) {
            throw new IllegalStateException("Historical rate does not match the requested pair/date");
        }
        return new Rate(rate, date);
    }
}
