package com.example.atlas.desktop;

import com.example.atlas.expense.ExpenseOptions;
import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.prefs.Preferences;
import static com.example.atlas.desktop.DesktopStyle.*;

/** Owns the spending headline, currency display settings and their saved preferences. */
final class SpendingSummaryPanel extends JPanel {
    private final JComboBox<String> mode = dropdown(
            new String[]{"Separate currencies", "Purchase-date conversion"}, "Total display mode");
    private final JComboBox<String> currency = currencyDropdown("Display currency");
    private final JPanel totals = plain(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel note = label("Original amounts · No currency conversion", 12, false, SECONDARY);
    private final JLabel context = label("All expenses", 12, false, SECONDARY);
    private final Preferences preferences;

    SpendingSummaryPanel(Preferences preferences, Runnable settingsChanged) {
        super(new BorderLayout());
        this.preferences = preferences;
        setOpaque(false);
        var panel = card(new BorderLayout(0, 12));
        var heading = plain(new BorderLayout());
        heading.add(label("NET SPENDING", 11, true, SECONDARY), BorderLayout.WEST);
        heading.add(context, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);

        totals.add(label("—", 30, true, INK));
        var amounts = new JScrollPane(totals, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        amounts.setBorder(BorderFactory.createEmptyBorder());
        amounts.setOpaque(false);
        amounts.getViewport().setOpaque(false);
        amounts.setPreferredSize(new Dimension(600, 64));
        panel.add(amounts, BorderLayout.CENTER);

        var settings = plain(new BorderLayout(12, 0));
        settings.add(note, BorderLayout.WEST);
        var controls = plain(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.add(label("Show totals", 12, false, SECONDARY));
        controls.add(mode);
        controls.add(currency);
        settings.add(controls, BorderLayout.EAST);
        panel.add(settings, BorderLayout.SOUTH);
        add(panel);

        restorePreferences();
        mode.addActionListener(event -> savePreferences(settingsChanged));
        currency.addActionListener(event -> savePreferences(settingsChanged));
    }

    boolean usesConversion() {
        return mode.getSelectedIndex() == 1;
    }

    String displayCurrency() {
        return (String) currency.getSelectedItem();
    }

    void setBusy(boolean busy) {
        mode.setEnabled(!busy);
        currency.setEnabled(!busy);
    }

    void showTotals(JsonNode currencyTotals, JsonNode convertedTotal, boolean filtered) {
        boolean converted = usesConversion() && convertedTotal != null
                && convertedTotal.path("complete").asBoolean();
        Iterable<JsonNode> displayedTotals = converted ? List.of(convertedTotal) : currencyTotals;

        totals.removeAll();
        for (var total : displayedTotals) {
            totals.add(amountCard(total));
        }
        if (!converted && currencyTotals.isEmpty()) {
            totals.add(label("—", 30, true, INK));
        }
        updateNote(converted);
        context.setText(filtered ? "Filtered expenses · All matching dates" : "All expenses · All time");
        totals.revalidate();
        totals.repaint();
    }

    private JPanel amountCard(JsonNode total) {
        var amountCard = plain(new BorderLayout(0, 4));
        var amountLine = plain(new FlowLayout(FlowLayout.LEFT, 0, 0));
        amountLine.add(label(money(total.path("amount")), 30, true, INK));
        amountLine.add(label("  " + total.path("currency").asText(), 13, true, SECONDARY));
        amountCard.add(amountLine, BorderLayout.CENTER);
        amountCard.setBorder(new EmptyBorder(0, 0, 0, 32));
        return amountCard;
    }

    private void updateNote(boolean converted) {
        note.setForeground(usesConversion() && !converted ? RED : SECONDARY);
        if (!usesConversion()) {
            note.setText("After reimbursements · No conversion");
            note.setToolTipText("Each currency is totalled separately.");
        } else if (converted) {
            note.setText("Purchase-date rates · Reference estimate");
            note.setToolTipText("Net purchase costs after reimbursements, converted at purchase-date rates. "
                    + "Previous published date (up to 7 days earlier) may be used.");
        } else {
            note.setText("Some rates unavailable · Original totals shown");
            note.setToolTipText("No partial converted total is shown. Refresh to retry missing rates, "
                    + "or switch to Separate currencies.");
        }
    }

    private void restorePreferences() {
        mode.setSelectedIndex(preferences.getBoolean("convertHistorical", false) ? 1 : 0);
        String savedCurrency = preferences.get("displayCurrency", "KZT");
        currency.setSelectedItem(ExpenseOptions.CURRENCIES.contains(savedCurrency) ? savedCurrency : "KZT");
        currency.setVisible(usesConversion());
    }

    private void savePreferences(Runnable settingsChanged) {
        preferences.putBoolean("convertHistorical", usesConversion());
        preferences.put("displayCurrency", displayCurrency());
        currency.setVisible(usesConversion());
        revalidate();
        settingsChanged.run();
    }
}
