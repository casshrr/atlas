package com.example.atlas.desktop;

import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import static com.example.atlas.desktop.DesktopStyle.*;

final class SpendingInsightsDialog {
    private final JDialog dialog;
    private final AtlasApiClient api;
    private final DatePicker from = new DatePicker("Insights from date", "Choose date", LocalDate.now().withDayOfMonth(1), false, LocalDate.now());
    private final DatePicker to = new DatePicker("Insights to date", "Choose date", LocalDate.now(), false, LocalDate.now());
    private final JComboBox<DateRangePreset> period = dropdown(new DateRangePreset[]{DateRangePreset.TODAY, DateRangePreset.LAST_7_DAYS,
            DateRangePreset.THIS_MONTH, DateRangePreset.LAST_MONTH, DateRangePreset.THIS_YEAR, DateRangePreset.CUSTOM}, "Insights date interval");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = label(" ", 12, false, SECONDARY);
    private final JButton update = button("Update insights", true);
    private boolean changingDates;

    SpendingInsightsDialog(JFrame owner, AtlasApiClient api) {
        dialog = new JDialog(owner, "Spending insights", Dialog.ModalityType.APPLICATION_MODAL); this.api = api;
    }

    void show() {
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        var root = new JPanel(new BorderLayout(0, 18)); root.setBackground(BACKGROUND); root.setBorder(new EmptyBorder(24, 28, 24, 28));
        var header = plain(new BorderLayout(0, 16));
        var title = plain(new GridLayout(2, 1, 0, 6)); title.add(label("Your spending, in perspective", 27, true, INK));
        title.add(label("Original currencies · Reimbursements reduce the original purchase period", 12, false, SECONDARY));
        header.add(title, BorderLayout.NORTH);
        var fields = plain(new GridLayout(1, 4, 12, 0));
        fields.add(field("Date interval", period)); fields.add(field("From date", from)); fields.add(field("To date", to)); fields.add(field(" ", update));
        header.add(fields, BorderLayout.SOUTH); root.add(header, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER); root.add(status, BorderLayout.SOUTH);
        period.setSelectedItem(DateRangePreset.THIS_MONTH);
        period.addActionListener(event -> {
            var selected = (DateRangePreset) period.getSelectedItem();
            if (!changingDates && selected != DateRangePreset.CUSTOM) {
                var range = selected.range(LocalDate.now()); changingDates = true;
                from.setDate(range.from()); to.setDate(range.to()); changingDates = false;
            }
        });
        java.beans.PropertyChangeListener changed = event -> { if (!changingDates) period.setSelectedItem(DateRangePreset.CUSTOM); };
        from.addPropertyChangeListener("date", changed); to.addPropertyChangeListener("date", changed);
        update.addActionListener(event -> load());
        dialog.setContentPane(root); dialog.setMinimumSize(new Dimension(860, 690)); dialog.setSize(980, 790);
        dialog.setLocationRelativeTo(dialog.getOwner()); load(); dialog.setVisible(true);
    }

    private void load() {
        var start = from.getDate(); var end = to.getDate();
        if (start.isAfter(end)) { status.setText("Choose a from date on or before the to date."); return; }
        setBusy(true); status.setText("Preparing your insights…");
        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception { return api.request("GET", "api/insights/spending?from=" + start + "&to=" + end, null); }
            @Override protected void done() {
                setBusy(false);
                try {
                    var report = get(); tabs.removeAll();
                    for (var currency : report.path("currencies")) tabs.addTab(currency.path("currency").asText(), currencyPanel(currency, report));
                    if (tabs.getTabCount() == 0) {
                        var empty = plain(new GridBagLayout()); empty.add(label("No spending in this interval or its comparison period.", 16, false, SECONDARY));
                        tabs.addTab("Overview", empty);
                    }
                    status.setText("Compared with " + report.path("previousFrom").asText() + " to " + report.path("previousTo").asText()
                            + " · Previous period has the same number of days.");
                } catch (Exception ex) {
                    var cause = ex.getCause() == null ? ex : ex.getCause();
                    status.setText(cause.getMessage() == null ? "Couldn’t load insights. Please try again." : cause.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        update.setEnabled(!busy); from.setEnabled(!busy); to.setEnabled(!busy); period.setEnabled(!busy);
    }

    private JPanel currencyPanel(JsonNode data, JsonNode report) {
        String currency = data.path("currency").asText();
        var panel = plain(new BorderLayout(0, 16)); panel.setBorder(new EmptyBorder(16, 8, 8, 8));
        var summary = card(new BorderLayout(0, 12));
        var figures = plain(new GridLayout(1, 3, 20, 0));
        figures.add(figure("NET SPENDING", money(data.path("netAmount")) + " " + currency));
        figures.add(figure("ORIGINAL SPENDING", money(data.path("grossAmount"))));
        figures.add(figure("REIMBURSED", money(data.path("reimbursedAmount"))));
        summary.add(figures, BorderLayout.CENTER);
        String comparison;
        if (data.path("changePercent").isNull()) {
            comparison = new BigDecimal(data.path("netAmount").asText()).signum() == 0 ? "No net spending in either period." : "No previous net spending to compare.";
        } else {
            var percent = new BigDecimal(data.path("changePercent").asText());
            comparison = percent.signum() == 0 ? "Net spending is unchanged from the previous period."
                    : percent.abs().toPlainString() + "% " + (percent.signum() > 0 ? "higher" : "lower") + " than the previous period.";
        }
        summary.add(label(comparison + "   ·   Daily average " + money(data.path("dailyAverage")) + " " + currency, 13, false, SECONDARY), BorderLayout.SOUTH);
        panel.add(summary, BorderLayout.NORTH);
        var details = plain(new BorderLayout(0, 16));
        var chartCard = card(new BorderLayout(0, 10));
        chartCard.add(label("Net spending over time", 15, true, INK), BorderLayout.NORTH);
        chartCard.add(new TrendChart(data.path("trend")), BorderLayout.CENTER); details.add(chartCard, BorderLayout.NORTH);
        var categories = card(new BorderLayout(0, 12)); categories.add(label("Where your money went", 15, true, INK), BorderLayout.NORTH);
        var model = new DefaultTableModel(new String[]{"Category", "Net spending · " + currency, "Share"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (var row : data.path("categories")) model.addRow(new Object[]{row.path("category").asText(), money(row.path("amount")), row.path("sharePercent").asText() + "%"});
        var table = new JTable(model); table.setRowHeight(32); table.setShowGrid(false); table.setFont(font(13, false));
        table.getTableHeader().setReorderingAllowed(false);
        var tableScroll = new JScrollPane(table); tableScroll.setPreferredSize(new Dimension(700, 190));
        tableScroll.getViewport().setBackground(Color.WHITE); categories.add(tableScroll, BorderLayout.CENTER);
        details.add(categories, BorderLayout.CENTER);
        var scroll = new JScrollPane(details); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(BACKGROUND); panel.add(scroll, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, 0)));
        return panel;
    }

    private JPanel figure(String title, String amount) {
        var panel = plain(new BorderLayout(0, 8)); panel.add(label(title, 11, true, SECONDARY), BorderLayout.NORTH);
        panel.add(label(amount, 23, true, INK), BorderLayout.CENTER); return panel;
    }

    private static String money(JsonNode value) { return new DecimalFormat("#,##0.00").format(new BigDecimal(value.asText())); }

    private static final class TrendChart extends JPanel {
        private final JsonNode points;
        TrendChart(JsonNode points) {
            this.points = points; setOpaque(false); setPreferredSize(new Dimension(700, 150));
            getAccessibleContext().setAccessibleName("Net spending trend");
            getAccessibleContext().setAccessibleDescription("Trend values are available from the spending insights API. Categories below provide exact totals.");
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            var g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 68, right = getWidth() - 18, top = 12, bottom = getHeight() - 30;
            if (right <= left || bottom <= top || points.isEmpty()) { g.dispose(); return; }
            double maximum = 0;
            for (var point : points) maximum = Math.max(maximum, point.path("amount").asDouble());
            if (maximum == 0) maximum = 1;
            g.setFont(font(10, false));
            for (int i = 0; i <= 2; i++) {
                int y = top + (bottom - top) * i / 2;
                g.setColor(LINE); g.drawLine(left, y, right, y); g.setColor(SECONDARY);
                g.drawString(new DecimalFormat("#,##0.##").format(maximum * (2 - i) / 2), 0, y + 4);
            }
            var line = new Path2D.Double(); int count = points.size();
            for (int i = 0; i < count; i++) {
                double x = count == 1 ? (left + right) / 2.0 : left + (right - left) * i / (double) (count - 1);
                double y = bottom - points.get(i).path("amount").asDouble() / maximum * (bottom - top);
                if (i == 0) line.moveTo(x, y); else line.lineTo(x, y);
                g.setColor(BLUE); g.fillOval((int) x - 3, (int) y - 3, 6, 6);
                if (i == 0 || i == count - 1 || (count > 5 && i == count / 2)) {
                    String text = points.get(i).path("label").asText(); g.setColor(SECONDARY);
                    g.drawString(text, Math.max(left, Math.min(right - g.getFontMetrics().stringWidth(text), (int) x - 18)), bottom + 20);
                }
            }
            g.setColor(BLUE); g.setStroke(new BasicStroke(2f)); g.draw(line); g.dispose();
        }
    }
}
