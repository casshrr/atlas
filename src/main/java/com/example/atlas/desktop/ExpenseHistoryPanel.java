package com.example.atlas.desktop;

import tools.jackson.databind.JsonNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import static com.example.atlas.desktop.DesktopStyle.*;

/** Presents expense rows and selection actions; does not fetch or change expenses. */
final class ExpenseHistoryPanel extends JPanel {
    private static final int FIRST_AMOUNT_COLUMN = 2;
    private static final int NET_AMOUNT_COLUMN = 4;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Date", "Category", "Original", "Reimbursed", "Net cost", "Currency", "Note"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<JsonNode> rows = new ArrayList<>();
    private final JButton edit = button("Edit", false);
    private final JButton delete = button("Delete", false);
    private final JButton reimburse = button("Reimbursements", false);
    private final JButton refresh = button("Refresh", false);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = plain(contentLayout);
    private final JLabel emptyTitle = label("Your story starts here", 22, true, INK);
    private final JLabel emptyMessage = label("Add your first expense to see where your money goes.", 14, false, SECONDARY);
    private boolean busy;

    ExpenseHistoryPanel(Runnable editExpense, Runnable deleteExpense, Runnable showReimbursements,
                        Runnable refreshExpenses) {
        super(new BorderLayout());
        setOpaque(false);
        var panel = card(new BorderLayout(0, 14));
        var heading = plain(new BorderLayout());
        heading.add(label("Expense history", 16, true, INK), BorderLayout.WEST);

        var actions = plain(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(reimburse);
        actions.add(edit);
        actions.add(delete);
        actions.add(refresh);
        delete.setForeground(RED);
        heading.add(actions, BorderLayout.EAST);
        panel.add(heading, BorderLayout.NORTH);

        configureTable();
        var scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        content.add(scroll, "table");
        content.add(createEmptyState(), "empty");
        panel.add(content, BorderLayout.CENTER);
        add(panel);

        edit.addActionListener(event -> editExpense.run());
        delete.addActionListener(event -> deleteExpense.run());
        reimburse.addActionListener(event -> showReimbursements.run());
        refresh.addActionListener(event -> refreshExpenses.run());
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        updateButtons();
    }

    JsonNode selectedExpense() {
        int selectedRow = table.getSelectedRow();
        return selectedRow < 0 ? null : rows.get(selectedRow);
    }

    void showExpenses(JsonNode expenses, boolean filtered) {
        rows.clear();
        model.setRowCount(0);
        for (var expense : expenses) {
            rows.add(expense);
            model.addRow(new Object[]{
                    LocalDate.parse(expense.path("spentOn").asText()).format(DATE_FORMAT),
                    expense.path("category").asText(),
                    money(expense.path("amount")),
                    money(expense.path("reimbursedAmount")),
                    money(expense.path("netAmount")),
                    expense.path("currency").asText(),
                    expense.path("description").asText("")
            });
        }
        emptyTitle.setText(filtered ? "No matching expenses" : "Your story starts here");
        emptyMessage.setText(filtered
                ? "Try another category or clear your date filters."
                : "Add your first expense to see where your money goes.");
        contentLayout.show(content, rows.isEmpty() ? "empty" : "table");
        updateButtons();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateButtons();
    }

    private void updateButtons() {
        boolean hasSelection = !busy && table.getSelectedRow() >= 0;
        edit.setEnabled(hasSelection);
        delete.setEnabled(hasSelection);
        reimburse.setEnabled(hasSelection);
        refresh.setEnabled(!busy);
    }

    private JPanel createEmptyState() {
        var empty = plain(new GridBagLayout());
        var stack = plain(new GridLayout(2, 1, 0, 12));
        emptyTitle.setHorizontalAlignment(SwingConstants.CENTER);
        emptyMessage.setHorizontalAlignment(SwingConstants.CENTER);
        stack.add(emptyTitle);
        stack.add(emptyMessage);
        empty.add(stack);
        return empty;
    }

    private void configureTable() {
        table.setFont(font(13, false));
        table.setForeground(INK);
        table.setBackground(Color.WHITE);
        table.setRowHeight(46);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xE8F2FF));
        table.setSelectionForeground(INK);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        var header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 34));
        header.setDefaultRenderer(new HeaderRenderer());
        table.setDefaultRenderer(Object.class, new ExpenseCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(5).setPreferredWidth(65);
        table.getColumnModel().getColumn(6).setPreferredWidth(200);
    }

    private static int alignment(int column) {
        return column >= FIRST_AMOUNT_COLUMN && column <= NET_AMOUNT_COLUMN
                ? SwingConstants.RIGHT : SwingConstants.LEFT;
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(owner, value, false, false, row, column);
            setFont(font(11, true));
            setForeground(SECONDARY);
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                    new EmptyBorder(0, 10, 0, 10)));
            setHorizontalAlignment(alignment(column));
            return this;
        }
    }

    private static final class ExpenseCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(owner, value, selected, focused, row, column);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(font(13, column == NET_AMOUNT_COLUMN));
            setHorizontalAlignment(alignment(column));
            if (!selected) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFAFAFC));
            }
            return this;
        }
    }
}
