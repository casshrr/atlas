package com.example.atlas.desktop;

import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import static com.example.atlas.desktop.DesktopStyle.*;

/** A separate received-money ledger, never a rewrite of the original purchase. */
final class ReimbursementsDialog {
    private final JDialog dialog;
    private final AtlasApiClient api;
    private final String expenseId;
    private final Runnable changed;
    private JsonNode expense;
    private final JLabel summary = label("Loading…", 16, true, INK);
    private final JLabel status = label(" ", 12, false, SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Received", "Amount", "Currency", "Note"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final List<JsonNode> rows = new ArrayList<>();
    private final JButton add = button("Record reimbursement", true);
    private final JButton remove = button("Remove selected", false);

    ReimbursementsDialog(JFrame owner, AtlasApiClient api, String expenseId, Runnable changed) {
        dialog = new JDialog(owner, "Reimbursements", Dialog.ModalityType.APPLICATION_MODAL);
        this.api = api; this.expenseId = expenseId; this.changed = changed;
    }

    void show() {
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        var root = new JPanel(new BorderLayout(0, 18)); root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(24, 24, 24, 24));
        var heading = plain(new GridLayout(3, 1, 0, 10));
        heading.add(label("Money received back", 25, true, INK)); heading.add(summary);
        heading.add(label("Partial repayments are welcome. Only record money you have received.", 13, false, SECONDARY));
        root.add(heading, BorderLayout.NORTH);
        table.setRowHeight(34); table.setFont(font(13, false)); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false); table.getTableHeader().setReorderingAllowed(false);
        var scroll = new JScrollPane(table); scroll.getViewport().setBackground(Color.WHITE); root.add(scroll, BorderLayout.CENTER);
        var footer = plain(new BorderLayout());
        var actions = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        add.addActionListener(event -> record()); remove.setForeground(RED);
        remove.addActionListener(event -> remove()); actions.add(remove); actions.add(add);
        footer.add(status, BorderLayout.NORTH); footer.add(actions, BorderLayout.SOUTH); root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root); dialog.setSize(780, 480); dialog.setLocationRelativeTo(dialog.getOwner());
        load(); dialog.setVisible(true);
    }

    private void load() {
        task(() -> new JsonNode[]{api.request("GET", "api/expenses/" + expenseId, null),
                api.request("GET", "api/expenses/" + expenseId + "/reimbursements", null)}, data -> {
            expense = data[0]; rows.clear(); model.setRowCount(0);
            for (var row : data[1]) { rows.add(row); model.addRow(new Object[]{row.path("receivedOn").asText(),
                    money(row.path("amount")), row.path("currency").asText(), row.path("note").asText("")}); }
            summary.setText("Original " + money(expense.path("amount")) + "   ·   Reimbursed " + money(expense.path("reimbursedAmount"))
                    + "   ·   Net " + money(expense.path("netAmount")) + " " + expense.path("currency").asText());
            add.setEnabled(new BigDecimal(expense.path("netAmount").asText()).signum() > 0);
            status.setText(rows.isEmpty() ? "No reimbursements recorded yet." : "Reimbursements reduce the purchase’s original period.");
        });
    }

    private String money(JsonNode value) { return new DecimalFormat("#,##0.00").format(new BigDecimal(value.asText())); }

    private void record() {
        if (expense == null) return;
        var form = new JDialog(dialog, "Record reimbursement", Dialog.ModalityType.APPLICATION_MODAL);
        var root = new JPanel(new BorderLayout(0, 20)); root.setBackground(BACKGROUND); root.setBorder(new EmptyBorder(24, 24, 24, 24));
        root.add(label("Received in " + expense.path("currency").asText() + " · Remaining " + money(expense.path("netAmount")), 14, true, INK), BorderLayout.NORTH);
        var amount = input("0.00", 20);
        var date = new DatePicker("Date received", "Choose date", LocalDate.now(), false, LocalDate.now());
        var note = input("Who paid you back? (optional)", 20);
        var fields = plain(new GridLayout(3, 1, 0, 14));
        fields.add(field("Amount received", amount)); fields.add(field("Date received", date)); fields.add(field("Note · optional", note));
        root.add(fields, BorderLayout.CENTER);
        var save = button("Save reimbursement", true);
        var cancel = button("Cancel", false); cancel.addActionListener(event -> form.dispose());
        var actions = plain(new FlowLayout(FlowLayout.RIGHT)); actions.add(cancel); actions.add(save); root.add(actions, BorderLayout.SOUTH);
        save.addActionListener(event -> {
            try {
                var value = new BigDecimal(amount.getText().strip());
                if (value.signum() <= 0 || value.scale() > 2 || value.precision() - value.scale() > 17) throw new IllegalArgumentException("Enter a positive amount with at most two decimal places");
                if (note.getText().strip().length() > 500) throw new IllegalArgumentException("Keep the note within 500 characters");
                var body = Map.of("amount", value, "currency", expense.path("currency").asText(), "receivedOn", date.getDate().toString(), "note", note.getText().strip());
                save.setEnabled(false); cancel.setEnabled(false);
                new SwingWorker<JsonNode, Void>() {
                    @Override protected JsonNode doInBackground() throws Exception { return api.request("POST", "api/expenses/" + expenseId + "/reimbursements", body); }
                    @Override protected void done() {
                        save.setEnabled(true); cancel.setEnabled(true);
                        try { get(); form.dispose(); load(); changed.run(); }
                        catch (Exception ex) { showError(form, ex); }
                    }
                }.execute();
            } catch (RuntimeException ex) { showError(form, ex); }
        });
        form.setContentPane(root); form.pack(); form.setLocationRelativeTo(dialog); form.setVisible(true);
    }

    private void remove() {
        int selected = table.getSelectedRow();
        if (selected < 0) { JOptionPane.showMessageDialog(dialog, "Select a reimbursement first."); return; }
        if (JOptionPane.showConfirmDialog(dialog, "Remove this reimbursement? The expense’s net cost will increase.",
                "Remove reimbursement", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        String id = rows.get(selected).path("id").asText();
        task(() -> api.request("DELETE", "api/expenses/" + expenseId + "/reimbursements/" + id, null), ignored -> { load(); changed.run(); });
    }

    private <T> void task(Callable<T> call, Consumer<T> success) {
        add.setEnabled(false); remove.setEnabled(false); status.setText("Updating…");
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return call.call(); }
            @Override protected void done() {
                add.setEnabled(true); remove.setEnabled(true);
                try { success.accept(get()); } catch (Exception ex) { status.setText("Couldn’t update reimbursements."); showError(dialog, ex); }
            }
        }.execute();
    }

    private void showError(Component owner, Exception ex) {
        var cause = ex.getCause() == null ? ex : ex.getCause();
        JOptionPane.showMessageDialog(owner, cause.getMessage() == null ? "Please check the amount and date." : cause.getMessage(), "Reimbursement", JOptionPane.ERROR_MESSAGE);
    }
}
