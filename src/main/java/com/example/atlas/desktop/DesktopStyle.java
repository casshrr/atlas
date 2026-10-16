package com.example.atlas.desktop;

import com.example.atlas.expense.ExpenseOptions;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.text.DecimalFormat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Small shared visual vocabulary for the desktop client. */
final class DesktopStyle {
    static final Color BACKGROUND = new Color(0xF5F5F7);
    static final Color INK = new Color(0x1D1D1F);
    static final Color SECONDARY = new Color(0x6E6E73);
    static final Color LINE = new Color(0xE8E8ED);
    static final Color BLUE = new Color(0x0071E3);
    static final Color RED = new Color(0xC43432);

    private DesktopStyle() {}

    static Font font(int size, boolean bold) {
        return new Font("Helvetica Neue", bold ? Font.BOLD : Font.PLAIN, size);
    }

    static JLabel label(String text, int size, boolean bold, Color color) {
        var label = new JLabel(text);
        label.setFont(font(size, bold)); label.setForeground(color);
        return label;
    }

    static JPanel plain(LayoutManager layout) {
        var panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }

    static JPanel card(LayoutManager layout) {
        var panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                var g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE); g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g.setColor(LINE); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
                g.dispose();
            }
        };
        panel.setOpaque(false); panel.setBorder(new EmptyBorder(20, 22, 20, 22));
        return panel;
    }

    static JTextField input(String placeholder, int columns) {
        var field = new JTextField(columns) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty() && !isFocusOwner()) {
                    var g = (Graphics2D) graphics.create();
                    g.setColor(SECONDARY); g.setFont(getFont());
                    var insets = getInsets();
                    g.drawString(placeholder, insets.left,
                            (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
                    g.dispose();
                }
            }
        };
        field.setFont(font(13, false)); field.setForeground(INK); field.setBackground(new Color(0xFAFAFC));
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                new EmptyBorder(9, 10, 9, 10)));
        field.setToolTipText(placeholder);
        field.getAccessibleContext().setAccessibleName(placeholder);
        return field;
    }

    static JPanel field(String title, JComponent input) {
        var panel = plain(new BorderLayout(0, 7));
        var label = label(title, 12, true, SECONDARY);
        label.setLabelFor(input); panel.add(label, BorderLayout.NORTH); panel.add(input, BorderLayout.CENTER);
        input.getAccessibleContext().setAccessibleName(title);
        return panel;
    }

    static <T> JComboBox<T> dropdown(T[] values, String accessibleName) {
        var combo = new JComboBox<>(values);
        combo.setFont(font(13, false)); combo.setBackground(Color.WHITE);
        combo.setPreferredSize(new Dimension(180, 38));
        combo.getAccessibleContext().setAccessibleName(accessibleName);
        return combo;
    }

    static JButton button(String text, boolean primary) {
        var button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                var g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color background = primary ? BLUE : new Color(0xEFEFF3);
                if (getModel().isPressed()) background = background.darker();
                else if (getModel().isRollover()) background = primary ? new Color(0x0077ED) : new Color(0xE4E4EB);
                g.setColor(isEnabled() ? background : new Color(0xEEEEF2));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                if (isFocusOwner()) {
                    g.setColor(new Color(0x80B9F4)); g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 12, 12);
                }
                g.dispose(); super.paintComponent(graphics);
            }
        };
        button.setFont(font(13, true)); button.setForeground(primary ? Color.WHITE : INK);
        button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
        button.setOpaque(false); button.setRolloverEnabled(true); button.setBorder(new EmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static String money(JsonNode value) {
        return new DecimalFormat("#,##0.00").format(new BigDecimal(value.asText("0")));
    }

    static JComboBox<String> currencyDropdown(String name) {
        var combo = dropdown(ExpenseOptions.CURRENCIES.toArray(String[]::new), name);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                String text = value == null ? "Choose currency" : ExpenseOptions.currencyLabel(value.toString());
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        return combo;
    }
}
