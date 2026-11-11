package com.college.ui.components;

import com.college.config.DBConnection;
import com.college.config.DatabaseProfile;
import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Diagnostic dialog displaying active database profile, location, integrity,
 * WAL journal mode, and schema migration status.
 */
public class DatabaseInfoDialog extends JDialog {

    public DatabaseInfoDialog(Frame parent) {
        super(parent, "Database Profile & Connection Details", true);
        initUI();
    }

    public DatabaseInfoDialog(Dialog parent) {
        super(parent, "Database Profile & Connection Details", true);
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setResizable(false);

        DatabaseProfile profile = DBConnection.getActiveProfile();
        String dbPathStr = DBConnection.getActiveDatabasePath();
        Path dbPath = Paths.get(dbPathStr).toAbsolutePath().normalize();
        File dbFile = dbPath.toFile();

        // Header panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(CampusTheme.PRIMARY);
        headerPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel titleLabel = new JLabel("Database Connection Information");
        titleLabel.setFont(CampusTheme.FONT_TITLE);
        titleLabel.setForeground(Color.WHITE);

        JLabel subLabel = new JLabel("Active Profile: " + profile.getUiBadge());
        subLabel.setFont(CampusTheme.FONT_BODY);
        subLabel.setForeground(new Color(0xD0, 0xE2, 0xFF));

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subLabel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Content panel
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(new EmptyBorder(20, 24, 20, 24));
        contentPanel.setBackground(CampusTheme.CARD);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Query database for metadata
        String journalMode = "Unknown";
        String foreignKeys = "Unknown";
        int migrationCount = 0;
        String latestMigration = "None";

        if (dbFile.exists()) {
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                    if (rs.next()) journalMode = rs.getString(1).toUpperCase();
                }
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                    if (rs.next()) foreignKeys = rs.getInt(1) == 1 ? "ON (Enforced)" : "OFF";
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*), max(version) FROM schema_migrations")) {
                    if (rs.next()) {
                        migrationCount = rs.getInt(1);
                        latestMigration = rs.getString(2);
                        if (latestMigration == null) latestMigration = "None";
                    }
                }
            } catch (Exception ignored) {
                // Keep default fallbacks if database cannot be queried
            }
        }

        long fileSizeBytes = dbFile.exists() ? dbFile.length() : 0;
        String fileSizeStr = fileSizeBytes < 1024 ? fileSizeBytes + " B"
                : fileSizeBytes < 1024 * 1024 ? String.format("%.2f KB", fileSizeBytes / 1024.0)
                : String.format("%.2f MB", fileSizeBytes / (1024.0 * 1024.0));

        int row = 0;
        addDetailRow(contentPanel, gbc, row++, "Active Profile:", profile.name() + " (" + profile.getUiBadge() + ")");
        addDetailRow(contentPanel, gbc, row++, "Profile Description:", profile.getDescription());
        addDetailRow(contentPanel, gbc, row++, "Database File:", dbFile.getName());
        addDetailRow(contentPanel, gbc, row++, "Absolute Path:", dbPath.toString());
        addDetailRow(contentPanel, gbc, row++, "File Status:", dbFile.exists() ? "Exists (" + fileSizeStr + ")" : "Not initialized yet");
        addDetailRow(contentPanel, gbc, row++, "SQLite Journal Mode:", journalMode);
        addDetailRow(contentPanel, gbc, row++, "Foreign Key Checks:", foreignKeys);
        addDetailRow(contentPanel, gbc, row++, "Applied Migrations:", migrationCount + " (Latest: " + latestMigration + ")");
        addDetailRow(contentPanel, gbc, row++, "Synthetic Demo Seeds:", profile.allowsSyntheticSeed() ? "Allowed & Loaded" : "Prohibited (Clean Schema)");

        add(contentPanel, BorderLayout.CENTER);

        // Footer panel
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footerPanel.setBackground(CampusTheme.CARD);
        footerPanel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, CampusTheme.BORDER),
                new EmptyBorder(8, 16, 8, 16)
        ));

        PrimaryButton closeBtn = new PrimaryButton("Close");
        closeBtn.addActionListener(e -> dispose());
        footerPanel.add(closeBtn);
        add(footerPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getParent());
    }

    private void addDetailRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(CampusTheme.FONT_BOLD);
        lbl.setForeground(CampusTheme.TEXT_SECONDARY);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JLabel val = new JLabel(value);
        val.setFont(CampusTheme.FONT_BODY);
        val.setForeground(CampusTheme.TEXT_PRIMARY);
        panel.add(val, gbc);
    }
}
