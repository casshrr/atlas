package com.example.atlas.capture;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Locale;
import java.util.Optional;

/** Stores the category rules explicitly approved by the user. */
@Repository
public class MerchantCategoryRepository {
    private final JdbcTemplate jdbc;

    public MerchantCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findCategory(String source, String merchant) {
        var categories = jdbc.queryForList(
                "SELECT category FROM merchant_categories WHERE merchant_key = ?",
                String.class, merchantKey(source, merchant));
        return categories.stream().findFirst();
    }

    public void remember(String source, String merchant, String category) {
        jdbc.update("""
                INSERT INTO merchant_categories(merchant_key, category) VALUES (?, ?)
                ON CONFLICT (merchant_key) DO UPDATE SET category = EXCLUDED.category
                """, merchantKey(source, merchant), category);
    }

    private String merchantKey(String source, String merchant) {
        String normalizedSource = source.strip().toLowerCase(Locale.ROOT);
        String normalizedMerchant = merchant.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalizedSource.length() + ":" + normalizedSource + ":" + normalizedMerchant;
    }
}
