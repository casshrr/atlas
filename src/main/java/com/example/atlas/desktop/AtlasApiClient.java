package com.example.atlas.desktop;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Desktop has no direct database access; all rules live in the backend. */
final class AtlasApiClient {
    private final URI base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    AtlasApiClient(String url) { base = URI.create(url.endsWith("/") ? url : url + "/"); }

    JsonNode request(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (body != null) builder.header("Content-Type", "application/json");
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String message = "Atlas returned HTTP " + response.statusCode();
            try {
                var problem = json.readTree(response.body());
                message = problem.path("detail").asText(message);
                if (problem.has("errors")) for (var error : problem.get("errors")) {
                    message += "\n" + error.path("field").asText() + ": " + error.path("message").asText();
                }
            } catch (Exception ignored) { /* A proxy may return non-JSON errors. */ }
            throw new IllegalStateException(message);
        }
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    /** The main window gets named responses instead of relying on array positions. */
    ExpenseOverview loadOverview(int page, String filters, boolean convert, String currency) throws Exception {
        JsonNode expensePage = request("GET", "api/expenses?page=" + page + "&size=25&" + filters, null);
        JsonNode currencyTotals = request("GET", "api/expenses/totals?" + filters, null);
        JsonNode convertedTotal = convert
                ? request("GET", "api/expenses/converted-total?currency=" + currency + "&" + filters, null)
                : null;
        return new ExpenseOverview(expensePage, currencyTotals, convertedTotal);
    }

    record ExpenseOverview(JsonNode expensePage, JsonNode currencyTotals, JsonNode convertedTotal) {}
}
