package com.example.atlas.desktop;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.*;

class ExpensePanelsTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void filterDraftsKeepPresetsValidationAndClearBehavior() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var applied = new AtomicInteger();
            var filters = new ExpenseFiltersPanel(applied::incrementAndGet);
            assertEquals("category=", filters.query());
            var category = find(filters, JComboBox.class, "Category");
            category.setSelectedItem("Food");
            assertEquals("category=Food", filters.query());
            assertEquals(0, applied.get());

            var period = find(filters, JComboBox.class, "Date interval");
            period.setSelectedItem(DateRangePreset.LAST_MONTH);
            LocalDate today = LocalDate.now();
            assertEquals("category=Food&from=" + today.minusMonths(1).withDayOfMonth(1)
                    + "&to=" + today.withDayOfMonth(1).minusDays(1), filters.query());
            find(filters, DatePicker.class, "From date").setDate(today);
            find(filters, DatePicker.class, "To date").setDate(today.minusDays(1));
            assertEquals(DateRangePreset.CUSTOM, period.getSelectedItem());
            assertThrows(IllegalArgumentException.class, filters::query);

            button(filters, "Clear").doClick();
            assertEquals("category=", filters.query());
            assertEquals(1, applied.get());
            filters.setBusy(true);
            assertFalse(button(filters, "Apply filters").isEnabled());
            assertFalse(category.isEnabled());
        });
    }

    @Test
    void expenseTableKeepsAmountsSelectionAndButtonStates() throws Exception {
        var expenses = json.readTree("""
                [{"id":"example","spentOn":"2026-09-14","category":"Food","amount":12.50,
                  "reimbursedAmount":2.50,"netAmount":10.00,"currency":"GBP","description":"Lunch"}]
                """);
        SwingUtilities.invokeAndWait(() -> {
            var edits = new AtomicInteger();
            var history = new ExpenseHistoryPanel(edits::incrementAndGet, () -> {}, () -> {}, () -> {});
            history.showExpenses(expenses, false);
            var table = find(history, JTable.class, null);
            assertEquals(7, table.getColumnCount());
            assertEquals("Reimbursed", table.getColumnName(3));
            assertEquals(DesktopStyle.money(expenses.get(0).path("netAmount")), table.getValueAt(0, 4));
            assertNull(history.selectedExpense());
            assertFalse(button(history, "Edit").isEnabled());
            table.setRowSelectionInterval(0, 0);
            assertEquals("example", history.selectedExpense().path("id").asText());
            button(history, "Edit").doClick();
            assertEquals(1, edits.get());
            history.setBusy(true);
            assertFalse(button(history, "Reimbursements").isEnabled());
            history.setBusy(false);
            assertTrue(button(history, "Reimbursements").isEnabled());
            history.showExpenses(json.createArrayNode(), true);
            assertNull(history.selectedExpense());
        });
    }

    @Test
    void summaryKeepsPreferencesFallbackAndCleanHeadline() throws Exception {
        var preferences = Preferences.userRoot().node("atlas-tests/" + UUID.randomUUID());
        preferences.putBoolean("convertHistorical", true);
        preferences.put("displayCurrency", "GBP");
        var original = json.readTree("[{\"currency\":\"KZT\",\"amount\":5000.00}]");
        var missing = json.readTree("{\"complete\":false,\"amount\":null}");
        var converted = json.readTree("{\"complete\":true,\"currency\":\"GBP\",\"amount\":8.75}");
        try {
            SwingUtilities.invokeAndWait(() -> {
                var changed = new AtomicInteger();
                var summary = new SpendingSummaryPanel(preferences, changed::incrementAndGet);
                assertTrue(summary.usesConversion());
                assertEquals("GBP", summary.displayCurrency());
                assertEquals(0, changed.get());
                summary.showTotals(original, missing, false);
                assertTrue(labels(summary).contains("Some rates unavailable · Original totals shown"));
                assertTrue(labels(summary).contains(DesktopStyle.money(original.get(0).path("amount"))));
                assertTrue(labels(summary).stream().noneMatch(text -> text.contains("Reimbursed")));
                summary.showTotals(original, converted, true);
                assertTrue(labels(summary).contains("Purchase-date rates · Reference estimate"));
                assertTrue(labels(summary).contains("Filtered expenses · All matching dates"));
                find(summary, JComboBox.class, "Total display mode").setSelectedIndex(0);
                assertFalse(preferences.getBoolean("convertHistorical", true));
                assertEquals(1, changed.get());
            });
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    void extractedEditorKeepsMoneyValidation() {
        assertEquals(new BigDecimal("12.50"), ExpenseEditorDialog.parseAmount(" 12.50 "));
        for (String value : new String[]{"", " ", "0", "-1", "1.234", "100000000000000000.00", "abc"}) {
            assertThrows(IllegalArgumentException.class, () -> ExpenseEditorDialog.parseAmount(value));
        }
    }

    private static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && (name == null
                    || name.equals(child.getAccessibleContext().getAccessibleName()))) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = find(container, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton button(Container root, String title) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && title.equals(button.getText())) return button;
            if (child instanceof Container container) {
                JButton found = button(container, title);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static java.util.List<String> labels(Container root) {
        var result = new ArrayList<String>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.add(label.getText());
            if (child instanceof Container container) result.addAll(labels(container));
        }
        return result;
    }
}
