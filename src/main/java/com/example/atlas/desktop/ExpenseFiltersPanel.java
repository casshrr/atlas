package com.example.atlas.desktop;

import com.example.atlas.expense.ExpenseOptions;
import javax.swing.*;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import static com.example.atlas.desktop.DesktopStyle.*;

/** Owns filter drafts. The main window decides when to apply them. */
final class ExpenseFiltersPanel extends JPanel {
    private final JComboBox<String> category = dropdown(categoryOptions(), "Filter category");
    private final JComboBox<DateRangePreset> period = dropdown(DateRangePreset.values(), "Date interval");
    private final DatePicker from = new DatePicker("From date", "Any start date", null, true, null);
    private final DatePicker to = new DatePicker("To date", "Any end date", null, true, null);
    private final JButton clear = button("Clear", false);
    private final JButton apply = button("Apply filters", true);
    private boolean settingRange;

    ExpenseFiltersPanel(Runnable applyFilters) {
        super(new BorderLayout());
        setOpaque(false);

        var panel = card(new BorderLayout(0, 14));
        var heading = plain(new BorderLayout());
        var introduction = plain(new GridLayout(2, 1, 0, 4));
        introduction.add(label("Filter expenses", 14, true, INK));
        introduction.add(label("Choose a category and purchase-date interval", 12, false, SECONDARY));
        heading.add(introduction, BorderLayout.WEST);

        var actions = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(clear);
        actions.add(apply);
        heading.add(actions, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);

        var fields = plain(new GridLayout(1, 4, 14, 0));
        fields.add(field("Category", category));
        fields.add(field("Date interval", period));
        fields.add(field("From date", from));
        fields.add(field("To date", to));
        panel.add(fields, BorderLayout.CENTER);
        add(panel);

        clear.addActionListener(event -> {
            reset();
            applyFilters.run();
        });
        apply.addActionListener(event -> applyFilters.run());
        period.addActionListener(event -> applyPreset());

        var markCustomRange = (java.beans.PropertyChangeListener) event -> {
            if (!settingRange) {
                period.setSelectedItem(DateRangePreset.CUSTOM);
            }
        };
        from.addPropertyChangeListener("date", markCustomRange);
        to.addPropertyChangeListener("date", markCustomRange);
    }

    String query() {
        if (from.getDate() != null && to.getDate() != null && from.getDate().isAfter(to.getDate())) {
            throw new IllegalArgumentException("Choose a from date that is on or before the to date.");
        }

        String selectedCategory = category.getSelectedIndex() == 0 ? "" : (String) category.getSelectedItem();
        String query = "category=" + URLEncoder.encode(selectedCategory, StandardCharsets.UTF_8);
        if (from.getDate() != null) {
            query += "&from=" + from.getDate();
        }
        if (to.getDate() != null) {
            query += "&to=" + to.getDate();
        }
        return query;
    }

    void setBusy(boolean busy) {
        category.setEnabled(!busy);
        period.setEnabled(!busy);
        from.setEnabled(!busy);
        to.setEnabled(!busy);
        clear.setEnabled(!busy);
        apply.setEnabled(!busy);
    }

    private void applyPreset() {
        var preset = (DateRangePreset) period.getSelectedItem();
        if (settingRange || preset == DateRangePreset.CUSTOM) {
            return;
        }

        var range = preset.range(LocalDate.now());
        settingRange = true;
        try {
            from.setDate(range.from());
            to.setDate(range.to());
        } finally {
            settingRange = false;
        }
    }

    private void reset() {
        category.setSelectedIndex(0);
        period.setSelectedItem(DateRangePreset.ALL_TIME);
        from.setDate(null);
        to.setDate(null);
        period.setSelectedItem(DateRangePreset.ALL_TIME);
    }

    private static String[] categoryOptions() {
        var options = new ArrayList<String>();
        options.add("All categories");
        options.addAll(ExpenseOptions.CATEGORIES);
        return options.toArray(String[]::new);
    }
}
