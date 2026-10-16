package com.example.atlas.desktop;

import com.example.atlas.expense.ExpenseOptions;
import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.example.atlas.desktop.DesktopStyle.*;

/** Collects and validates a draft. Saving it remains the main window's responsibility. */
final class ExpenseEditorDialog {
    private final JFrame owner;
    private final JTextField amount = input("0.00", 18);
    private final JComboBox<String> currency = currencyDropdown("Expense currency");
    private final JComboBox<String> category = dropdown(
            ExpenseOptions.CATEGORIES.toArray(String[]::new), "Expense category");
    private final JTextField description = input("What was it for?", 18);
    private final DatePicker spentOn;

    ExpenseEditorDialog(JFrame owner, JsonNode existing) {
        this.owner = owner;
        spentOn = new DatePicker("Purchase date", "Choose date",
                existing == null ? LocalDate.now() : LocalDate.parse(existing.path("spentOn").asText()),
                false, LocalDate.now());

        category.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                String text = value == null ? "Choose a category" : value.toString();
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        category.setSelectedIndex(-1);
        populate(existing);
    }

    Map<String, Object> show(boolean editing) {
        var form = createForm();
        String saveLabel = editing ? "Save changes" : "Add expense";
        String title = editing ? "Edit expense" : "Add expense";

        while (JOptionPane.showOptionDialog(owner, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new String[]{saveLabel, "Cancel"}, saveLabel) == 0) {
            try {
                return requestBody();
            } catch (RuntimeException ex) {
                JOptionPane.showMessageDialog(owner, ex.getMessage(), "Atlas", JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }

    private void populate(JsonNode existing) {
        if (existing != null) {
            amount.setText(existing.path("amount").asText());
            String savedCategory = ExpenseOptions.category(existing.path("category").asText());
            if (ExpenseOptions.CATEGORIES.contains(savedCategory)) {
                category.setSelectedItem(savedCategory);
            }
            description.setText(existing.path("description").asText(""));
        }
        String savedCurrency = existing == null ? "KZT" : existing.path("currency").asText();
        currency.setSelectedItem(ExpenseOptions.CURRENCIES.contains(savedCurrency) ? savedCurrency : null);
    }

    private JPanel createForm() {
        var form = plain(new BorderLayout(0, 20));
        form.setBorder(new EmptyBorder(12, 8, 12, 8));
        form.add(label("The small details make the bigger picture.", 13, false, SECONDARY), BorderLayout.NORTH);
        var fields = plain(new GridLayout(0, 1, 0, 14));
        var moneyFields = plain(new GridLayout(1, 2, 14, 0));
        moneyFields.add(field("Amount", amount));
        moneyFields.add(field("Currency", currency));
        fields.add(moneyFields);
        fields.add(field("Category", category));
        fields.add(field("Note · optional", description));
        fields.add(field("Purchase date", spentOn));
        form.add(fields, BorderLayout.CENTER);
        return form;
    }

    private Map<String, Object> requestBody() {
        BigDecimal value = parseAmount(amount.getText());
        LocalDate date = spentOn.getDate();
        String currencyCode = (String) currency.getSelectedItem();
        String categoryName = (String) category.getSelectedItem();
        String note = description.getText().strip();

        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date cannot be in the future.");
        }
        if (currencyCode == null) {
            throw new IllegalArgumentException("Choose a currency.");
        }
        if (categoryName == null) {
            throw new IllegalArgumentException("Choose a category.");
        }
        if (note.length() > 500) {
            throw new IllegalArgumentException("Description must be at most 500 characters.");
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("amount", value);
        body.put("currency", currencyCode);
        body.put("category", categoryName);
        body.put("description", note);
        body.put("spentOn", date.toString());
        return body;
    }

    static BigDecimal parseAmount(String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Enter an amount.");
        }
        var value = new BigDecimal(text.strip());
        if (value.signum() <= 0 || value.scale() > 2 || value.precision() - value.scale() > 17) {
            throw new IllegalArgumentException("Use a positive amount with at most two decimal places.");
        }
        return value;
    }
}
