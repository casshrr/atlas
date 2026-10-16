package com.example.atlas.desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import static com.example.atlas.desktop.DesktopStyle.*;

/** Calendar selection without a typed-date parser or a third-party UI dependency. */
final class DatePicker extends JButton {
    private LocalDate date;
    private final boolean nullable;
    private final LocalDate maximum;
    private final String emptyText;

    DatePicker(String accessibleName, String emptyText, LocalDate initial, boolean nullable, LocalDate maximum) {
        this.emptyText = emptyText; this.nullable = nullable; this.maximum = maximum;
        setFont(font(13, false)); setBackground(Color.WHITE); setForeground(INK);
        setBorder(new EmptyBorder(9, 10, 9, 10));
        getAccessibleContext().setAccessibleName(accessibleName);
        setToolTipText("Choose a date from the calendar");
        setDate(initial);
        addActionListener(event -> openCalendar());
    }

    LocalDate getDate() { return date; }

    void setDate(LocalDate value) {
        if (value == null && !nullable) throw new IllegalArgumentException("Choose a date");
        if (value != null && maximum != null && value.isAfter(maximum)) throw new IllegalArgumentException("Date is in the future");
        LocalDate previous = date; date = value;
        setText((date == null ? emptyText : date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) + "  ▾");
        firePropertyChange("date", previous, value);
    }

    private void openCalendar() {
        var popup = new JDialog(SwingUtilities.getWindowAncestor(this), "Choose a date", Dialog.ModalityType.APPLICATION_MODAL);
        popup.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        var panel = new JPanel(new BorderLayout(8, 12));
        panel.setBackground(Color.WHITE); panel.setBorder(new EmptyBorder(14, 14, 14, 14));
        var shown = date == null ? LocalDate.now() : date;
        var month = new JComboBox<>(Month.values());
        month.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, value instanceof Month m
                        ? m.getDisplayName(TextStyle.FULL, Locale.ENGLISH) : value, index, selected, focus);
            }
        });
        month.setSelectedItem(shown.getMonth());
        var year = new JComboBox<Integer>();
        for (int y = Math.min(LocalDate.now().getYear() - 100, shown.getYear());
                y <= Math.max(LocalDate.now().getYear() + 10, shown.getYear()); y++) year.addItem(y);
        year.setSelectedItem(shown.getYear());
        month.getAccessibleContext().setAccessibleName("Calendar month");
        year.getAccessibleContext().setAccessibleName("Calendar year");
        var navigation = plain(new BorderLayout(8, 0));
        navigation.add(month, BorderLayout.CENTER); navigation.add(year, BorderLayout.EAST);
        panel.add(navigation, BorderLayout.NORTH);
        var grid = new JPanel(new GridLayout(0, 7, 3, 3)); grid.setBackground(Color.WHITE);
        Runnable render = () -> {
            grid.removeAll();
            for (String day : new String[]{"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) {
                var heading = label(day, 11, true, SECONDARY); heading.setHorizontalAlignment(SwingConstants.CENTER); grid.add(heading);
            }
            var first = LocalDate.of((Integer) year.getSelectedItem(), ((Month) month.getSelectedItem()).getValue(), 1);
            for (int i = 1; i < first.getDayOfWeek().getValue(); i++) grid.add(new JLabel());
            for (int day = 1; day <= first.lengthOfMonth(); day++) {
                LocalDate candidate = first.withDayOfMonth(day);
                var button = DesktopStyle.button(String.valueOf(day), candidate.equals(date));
                button.setBorder(new EmptyBorder(6, 5, 6, 5)); button.setPreferredSize(new Dimension(36, 32));
                button.getAccessibleContext().setAccessibleName(candidate.toString());
                button.setEnabled(maximum == null || !candidate.isAfter(maximum));
                button.addActionListener(event -> { setDate(candidate); popup.dispose(); });
                grid.add(button);
            }
            while (grid.getComponentCount() < 49) grid.add(new JLabel());
            grid.revalidate(); grid.repaint(); popup.pack();
        };
        month.addActionListener(event -> render.run()); year.addActionListener(event -> render.run());
        panel.add(grid, BorderLayout.CENTER);
        var actions = plain(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        var today = DesktopStyle.button("Today", false);
        today.addActionListener(event -> { setDate(LocalDate.now()); popup.dispose(); }); actions.add(today);
        if (nullable) {
            var clear = DesktopStyle.button("Any date", false);
            clear.addActionListener(event -> { setDate(null); popup.dispose(); }); actions.add(clear);
        }
        panel.add(actions, BorderLayout.SOUTH); popup.setContentPane(panel); render.run();
        popup.getRootPane().registerKeyboardAction(event -> popup.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        popup.setResizable(false); popup.setLocationRelativeTo(this); popup.setVisible(true);
    }
}
