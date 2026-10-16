package com.example.atlas.desktop;

import com.example.atlas.expense.ExpenseOptions;
import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import static com.example.atlas.desktop.DesktopStyle.*;

final class PhoneInboxDialog {
    private final JDialog dialog;
    private final AtlasApiClient api;
    private final FolderCaptureBridge bridge;
    private final Runnable changed;
    private final JLabel connection = label("", 12, false, SECONDARY);
    private final JLabel status = label("Only confirmed purchases affect your spending.", 12, false, SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Date", "Source", "Merchant", "Amount", "Why review?"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable table = new JTable(model);
    private final List<JsonNode> rows = new ArrayList<>();
    private final JButton approve = button("Review purchase", true), dismiss = button("Dismiss", false), refresh = button("Refresh", false);
    private final javax.swing.Timer timer;
    private boolean loading;

    PhoneInboxDialog(JFrame owner, AtlasApiClient api, FolderCaptureBridge bridge, Runnable changed) {
        this.api = api; this.bridge = bridge; this.changed = changed;
        dialog = new JDialog(owner, "iPhone · Capture & review", true);
        timer = new javax.swing.Timer(10000, event -> {
            connection.setText(bridge.message());
            if (dialog.isActive() && !loading) load();
        });
    }
    void show() {
        var root = new JPanel(new BorderLayout(0, 18)); root.setBackground(BACKGROUND); root.setBorder(new EmptyBorder(24, 24, 24, 24));
        var top = plain(new GridLayout(0, 1, 0, 10));
        top.add(label("From your iPhone, with a little help from iCloud.", 21, true, INK));
        top.add(label("A Shortcut saves payments to a shared folder. Atlas checks them while this app is open.", 13, false, SECONDARY));
        connection.setText(bridge.message()); top.add(connection);
        var actions = plain(new FlowLayout(FlowLayout.LEFT, 8, 0));
        var choose = button("Choose iCloud folder…", false);
        choose.addActionListener(event -> {
            var chooser = new JFileChooser(); chooser.setDialogTitle("Choose iCloud Drive / Shortcuts / Atlas"); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                Path folder = chooser.getSelectedFile().toPath();
                if (JOptionPane.showConfirmDialog(dialog, "Read payment files from this folder?\n" + folder + "\n\nKeep it private. Captured notifications can contain financial information.", "Connect iPhone folder", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) bridge.choose(folder);
            }
        });
        var disconnect = button("Disconnect", false); disconnect.addActionListener(event -> { bridge.disconnect(); connection.setText(bridge.message()); });
        var help = button("Setup instructions", false); help.addActionListener(event -> setup());
        actions.add(choose); actions.add(disconnect); actions.add(help); top.add(actions); root.add(top, BorderLayout.NORTH);
        table.setRowHeight(42); table.setFont(font(13, false)); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        var bottom = plain(new BorderLayout(0, 12)); bottom.add(status, BorderLayout.NORTH);
        var buttons = plain(new FlowLayout(FlowLayout.RIGHT)); buttons.add(dismiss); buttons.add(refresh); buttons.add(approve); bottom.add(buttons, BorderLayout.SOUTH); root.add(bottom, BorderLayout.SOUTH);
        approve.addActionListener(event -> { if (table.getSelectedRow() >= 0) review(rows.get(table.getSelectedRow())); });
        dismiss.addActionListener(event -> {
            if (table.getSelectedRow() < 0) return;
            String id = rows.get(table.getSelectedRow()).path("id").asText();
            if (JOptionPane.showConfirmDialog(dialog, "Dismiss this capture without adding an expense?", "Dismiss capture", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                perform("api/captures/" + id + "/dismiss", Map.of(), null);
        });
        refresh.addActionListener(event -> { bridge.scanNow(); load(); });
        dialog.setContentPane(root); dialog.setSize(1060, 650); dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosed(java.awt.event.WindowEvent e) { timer.stop(); } });
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); timer.start(); load(); dialog.setVisible(true);
    }
    private void load() {
        String selectedId = table.getSelectedRow() >= 0 && table.getSelectedRow() < rows.size() ? rows.get(table.getSelectedRow()).path("id").asText() : null;
        busy(true);
        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception { return api.request("GET", "api/captures", null); }
            @Override protected void done() {
                busy(false);
                try {
                    var data = get(); rows.clear(); model.setRowCount(0);
                    for (var row : data) { rows.add(row); model.addRow(new Object[]{row.path("spentOn").asText(), row.path("source").asText(), text(row, "merchant"), text(row, "amount") + " " + text(row, "currency"), row.path("reason").asText()}); }
                    for (int index = 0; index < rows.size(); index++) if (rows.get(index).path("id").asText().equals(selectedId)) table.setRowSelectionInterval(index, index);
                    updateButtons();
                    status.setText(rows.isEmpty() ? "All caught up. New unclear payments will appear here." : "Needs review · First 100 shown. Confirmed purchases appear in Expenses.");
                } catch (Exception ex) { error(ex); }
            }
        }.execute();
    }
    private void review(JsonNode row) {
        var form = new JDialog(dialog, "Confirm purchase", true);
        var root = new JPanel(new BorderLayout(0, 16)); root.setBackground(BACKGROUND); root.setBorder(new EmptyBorder(24, 24, 24, 24));
        var original = new JTextArea(row.path("rawText").asText(), 5, 45); original.setEditable(false); original.setLineWrap(true); original.setWrapStyleWord(true);
        root.add(field("Original notification · check that payment completed", new JScrollPane(original)), BorderLayout.NORTH);
        var amount = input("Amount", 18); amount.setText(text(row, "amount"));
        var currency = dropdown(ExpenseOptions.CURRENCIES.toArray(String[]::new), "Currency"); currency.setSelectedItem(text(row, "currency").isBlank() ? null : text(row, "currency"));
        var category = dropdown(ExpenseOptions.CATEGORIES.toArray(String[]::new), "Category"); category.setSelectedItem(text(row, "category").isBlank() ? null : text(row, "category"));
        var merchant = input("Merchant", 25); merchant.setText(text(row, "merchant"));
        var date = new DatePicker("Purchase date", "Choose date", LocalDate.parse(row.path("spentOn").asText()), false, LocalDate.now());
        var note = input("Note (optional)", 25); note.setText(text(row, "merchant"));
        var fields = plain(new GridLayout(3, 2, 14, 14)); fields.add(field("Amount", amount)); fields.add(field("Currency", currency)); fields.add(field("Merchant", merchant)); fields.add(field("Category", category)); fields.add(field("Purchase date", date)); fields.add(field("Note", note)); root.add(fields, BorderLayout.CENTER);
        var south = plain(new GridLayout(0, 1, 0, 8));
        var remember = new JCheckBox("Remember this category for this merchant and source"); remember.setOpaque(false);
        var duplicate = new JCheckBox("I checked: this is a separate purchase even if another amount matches"); duplicate.setOpaque(false);
        south.add(remember); south.add(duplicate);
        var save = button("Confirm completed purchase", true); var cancel = button("Cancel", false); cancel.addActionListener(event -> form.dispose());
        var actions = plain(new FlowLayout(FlowLayout.RIGHT)); actions.add(cancel); actions.add(save); south.add(actions); root.add(south, BorderLayout.SOUTH);
        save.addActionListener(event -> {
            try {
                if (currency.getSelectedItem() == null || category.getSelectedItem() == null || merchant.getText().isBlank()) throw new IllegalArgumentException("Choose currency, category and merchant");
                var expense = Map.of("amount", new BigDecimal(amount.getText().strip()), "currency", currency.getSelectedItem(), "category", category.getSelectedItem(), "spentOn", date.getDate().toString(), "description", note.getText().strip());
                var body = Map.of("expense", expense, "merchant", merchant.getText().strip(), "rememberCategory", remember.isSelected(), "allowPossibleDuplicate", duplicate.isSelected());
                save.setEnabled(false); cancel.setEnabled(false);
                new SwingWorker<JsonNode, Void>() {
                    @Override protected JsonNode doInBackground() throws Exception { return api.request("POST", "api/captures/" + row.path("id").asText() + "/approve", body); }
                    @Override protected void done() { save.setEnabled(true); cancel.setEnabled(true); try { get(); form.dispose(); load(); changed.run(); } catch (Exception ex) { error(ex); } }
                }.execute();
            } catch (Exception ex) { error(ex); }
        });
        form.setContentPane(root); form.pack(); form.setLocationRelativeTo(dialog); form.setVisible(true);
    }
    private void perform(String path, Object body, Runnable done) {
        busy(true);
        new SwingWorker<JsonNode, Void>() {
            @Override protected JsonNode doInBackground() throws Exception { return api.request("POST", path, body); }
            @Override protected void done() { busy(false); try { get(); if (done != null) done.run(); load(); changed.run(); } catch (Exception ex) { error(ex); } }
        }.execute();
    }
    private void busy(boolean value) { loading = value; updateButtons(); }
    private void updateButtons() { approve.setEnabled(!loading && table.getSelectedRow() >= 0); dismiss.setEnabled(!loading && table.getSelectedRow() >= 0); refresh.setEnabled(!loading); }
    private void setup() {
        try (var resource = getClass().getResourceAsStream("/iphone-setup.txt")) {
            if (resource == null) throw new IllegalStateException("The setup guide is missing from this build");
            var text = new JTextArea(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), 27, 75);
            text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setFont(font(13, false)); text.setCaretPosition(0);
            JOptionPane.showMessageDialog(dialog, new JScrollPane(text), "iPhone setup · iOS 26", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception ex) { error(ex); }
    }
    private static String text(JsonNode row, String key) { return row.path(key).isNull() ? "" : row.path(key).asText(""); }
    private void error(Exception ex) { var cause = ex.getCause() == null ? ex : ex.getCause(); JOptionPane.showMessageDialog(dialog, cause.getMessage(), "iPhone import", JOptionPane.ERROR_MESSAGE); }
}
