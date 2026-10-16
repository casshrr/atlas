package com.example.atlas.desktop;

import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import static com.example.atlas.desktop.DesktopStyle.*;

/** Coordinates the desktop screens and API calls. Each panel owns its own presentation. */
public final class DesktopApplication {
    private final JFrame frame = new JFrame("Atlas · Personal expenses");
    private final AtlasApiClient api;
    private final DesktopBackend backend;
    private final FolderCaptureBridge phoneBridge;
    private final List<JButton> headerButtons = new ArrayList<>();
    private final JLabel status = label("Loading your expenses…", 12, false, SECONDARY);
    private final JLabel pageLabel = label(" ", 12, false, SECONDARY);
    private final JPanel pagination = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JButton previous = button("Previous", false);
    private final JButton next = button("Next", false);
    private final ExpenseFiltersPanel filters = new ExpenseFiltersPanel(this::applyFilters);
    private final SpendingSummaryPanel summary = new SpendingSummaryPanel(
            Preferences.userNodeForPackage(DesktopApplication.class), this::refresh);
    private final ExpenseHistoryPanel history = new ExpenseHistoryPanel(
            this::editSelectedExpense, this::deleteSelectedExpense, this::showReimbursements, this::refresh);

    private boolean busy;
    private String appliedFilters = "category=";
    private int page;
    private int totalPages;

    DesktopApplication(DesktopBackend backend) {
        this.backend = backend;
        api = new AtlasApiClient(backend.url());
        phoneBridge = new FolderCaptureBridge(api, () -> {
            if (!busy) {
                refresh();
            }
        });
    }

    public static void main(String[] args) {
        DesktopLauncher.launch(args);
    }

    void show() {
        configureWindow();
        var root = new JPanel(new BorderLayout(0, 22));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(26, 34, 24, 34));
        root.add(createHeader(), BorderLayout.NORTH);

        var main = plain(new BorderLayout(0, 18));
        main.add(filters, BorderLayout.NORTH);
        main.add(history, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        root.add(createFooter(), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        refresh();
        phoneBridge.start();
    }

    private void configureWindow() {
        frame.setMinimumSize(new Dimension(1030, 740));
        frame.setPreferredSize(new Dimension(1140, 820));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                close();
            }
        });
    }

    private JPanel createHeader() {
        var top = plain(new BorderLayout(0, 18));
        var brand = plain(new BorderLayout());
        brand.add(label("atlas", 21, true, INK), BorderLayout.WEST);
        brand.add(label("A little clarity, every day.", 12, false, SECONDARY), BorderLayout.EAST);
        top.add(brand, BorderLayout.NORTH);

        var heading = plain(new BorderLayout());
        var introduction = plain(new GridLayout(2, 1, 0, 6));
        introduction.add(label("Expenses", 34, true, INK));
        introduction.add(label("Make room for a clearer picture of your spending.", 14, false, SECONDARY));
        heading.add(introduction, BorderLayout.WEST);
        var actions = plain(new FlowLayout(FlowLayout.RIGHT, 0, 10));
        actions.add(headerButton("iPhone inbox", false,
                () -> new PhoneInboxDialog(frame, api, phoneBridge, this::refresh).show()));
        actions.add(Box.createHorizontalStrut(10));
        actions.add(headerButton("Spending insights", false,
                () -> new SpendingInsightsDialog(frame, api).show()));
        actions.add(Box.createHorizontalStrut(10));
        actions.add(headerButton("+  Add expense", true, () -> editExpense(null)));
        heading.add(actions, BorderLayout.EAST);
        top.add(heading, BorderLayout.CENTER);
        top.add(summary, BorderLayout.SOUTH);
        return top;
    }

    private JButton headerButton(String title, boolean primary, Runnable action) {
        var button = button(title, primary);
        button.addActionListener(event -> action.run());
        headerButtons.add(button);
        return button;
    }

    private JPanel createFooter() {
        var footer = plain(new BorderLayout());
        status.setVisible(false);
        footer.add(status, BorderLayout.WEST);
        previous.addActionListener(event -> {
            if (page > 0) {
                page--;
                refresh();
            }
        });
        next.addActionListener(event -> {
            if (page + 1 < totalPages) {
                page++;
                refresh();
            }
        });
        pagination.add(previous);
        pagination.add(pageLabel);
        pagination.add(next);
        footer.add(pagination, BorderLayout.EAST);
        return footer;
    }

    private void updateControls() {
        headerButtons.forEach(button -> button.setEnabled(!busy));
        filters.setBusy(busy);
        history.setBusy(busy);
        summary.setBusy(busy);
        previous.setEnabled(!busy && page > 0);
        next.setEnabled(!busy && page + 1 < totalPages);
    }

    private void applyFilters() {
        try {
            appliedFilters = filters.query();
            page = 0;
            refresh();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void refresh() {
        String query = appliedFilters;
        boolean conversion = summary.usesConversion();
        String currency = summary.displayCurrency();
        run(() -> api.loadOverview(page, query, conversion, currency), this::showOverview);
    }

    private void showOverview(AtlasApiClient.ExpenseOverview overview) {
        JsonNode expensePage = overview.expensePage();
        totalPages = expensePage.path("totalPages").asInt();
        if (page > 0 && page >= totalPages) {
            page = Math.max(0, totalPages - 1);
            refresh();
            return;
        }

        boolean filtered = !appliedFilters.equals("category=");
        history.showExpenses(expensePage.path("content"), filtered);
        summary.showTotals(overview.currencyTotals(), overview.convertedTotal(), filtered);
        pageLabel.setText("Page " + (totalPages == 0 ? 0 : page + 1) + " of " + totalPages);
        pagination.setVisible(totalPages > 1);
        status.setText("");
        status.setVisible(false);
        updateControls();
    }

    private void editSelectedExpense() {
        JsonNode expense = selectedExpense();
        if (expense != null) {
            editExpense(expense);
        }
    }

    private void editExpense(JsonNode existing) {
        var body = new ExpenseEditorDialog(frame, existing).show(existing != null);
        if (body == null) {
            return;
        }
        String method = existing == null ? "POST" : "PUT";
        String path = existing == null ? "api/expenses" : "api/expenses/" + existing.path("id").asText();
        run(() -> api.request(method, path, body), ignored -> refresh());
    }

    private void showReimbursements() {
        JsonNode expense = selectedExpense();
        if (expense != null) {
            new ReimbursementsDialog(frame, api, expense.path("id").asText(), this::refresh).show();
        }
    }

    private void deleteSelectedExpense() {
        JsonNode expense = selectedExpense();
        if (expense == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(frame, "Delete this expense permanently?",
                "Delete expense", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            run(() -> api.request("DELETE", "api/expenses/" + expense.path("id").asText(), null),
                    ignored -> refresh());
        }
    }

    private JsonNode selectedExpense() {
        JsonNode expense = history.selectedExpense();
        if (expense == null) {
            showError("Select an expense first.");
        }
        return expense;
    }

    private <T> void run(Callable<T> task, Consumer<T> onSuccess) {
        busy = true;
        updateControls();
        status.setForeground(SECONDARY);
        status.setText("Updating…");
        status.setVisible(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                busy = false;
                updateControls();
                try {
                    onSuccess.accept(get());
                } catch (Exception ex) {
                    showRequestFailure(ex);
                }
            }
        }.execute();
    }

    private void showRequestFailure(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        status.setForeground(RED);
        status.setVisible(true);
        if (cause instanceof IOException) {
            status.setText("Couldn’t connect to Atlas. Please try Refresh.");
        } else {
            status.setText("Couldn’t complete the request. Please review the message and try again.");
            showError(cause.getMessage() == null ? "Something went wrong. Please try again." : cause.getMessage());
        }
    }

    private void showError(String message) {
        String detail = message == null || message.isBlank() ? "Check the entered values and try again." : message;
        JOptionPane.showMessageDialog(frame, detail, "Atlas", JOptionPane.ERROR_MESSAGE);
    }

    private void close() {
        busy = true;
        updateControls();
        status.setText("Closing Atlas…");
        status.setVisible(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                phoneBridge.close();
                backend.close();
                return null;
            }

            @Override
            protected void done() {
                frame.dispose();
                System.exit(0);
            }
        }.execute();
    }
}
