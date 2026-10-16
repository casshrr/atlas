package com.example.atlas.desktop;

import javax.swing.*;
import java.awt.*;
import javax.swing.border.EmptyBorder;
import static com.example.atlas.desktop.DesktopStyle.*;

final class DesktopLauncher {
    private DesktopLauncher() {}

    static void launch(String[] args) {
        System.setProperty("java.awt.headless", "false");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            var startup = new JFrame("Atlas");
            startup.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            var panel = new JPanel(new BorderLayout(0, 22)); panel.setBackground(BACKGROUND);
            panel.setBorder(new EmptyBorder(32, 36, 32, 36));
            panel.add(label("Welcome to Atlas", 26, true, INK), BorderLayout.NORTH);
            panel.add(label("Getting your space ready…", 14, false, SECONDARY), BorderLayout.CENTER);
            var progress = new JProgressBar(); progress.setIndeterminate(true); panel.add(progress, BorderLayout.SOUTH);
            startup.setContentPane(panel); startup.pack(); startup.setSize(460, 220);
            startup.setLocationRelativeTo(null); startup.setVisible(true);
            new SwingWorker<DesktopBackend, Void>() {
                @Override protected DesktopBackend doInBackground() {
                    return DesktopBackend.start(System.getenv("ATLAS_API_URL"), args);
                }
                @Override protected void done() {
                    try {
                        DesktopBackend backend = get();
                        startup.dispose(); new DesktopApplication(backend).show();
                    } catch (Exception ex) {
                        startup.dispose();
                        JOptionPane.showMessageDialog(null,
                                "Atlas couldn’t open its database.\nMake sure PostgreSQL is running and the Atlas database is available.\n"
                                + "If you use custom connection settings, check DB_URL, DB_USER and DB_PASSWORD.",
                                "Couldn’t start Atlas", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                }
            }.execute();
        });
    }
}
