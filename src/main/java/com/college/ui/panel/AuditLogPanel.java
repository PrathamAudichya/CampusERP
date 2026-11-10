package com.college.ui.panel;

import com.college.model.AuditLog;
import com.college.model.User;
import com.college.service.AuditService;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Immutable system audit trail viewer panel (Admin only).
 */
public class AuditLogPanel extends JPanel {

    private final User currentUser;
    private final AuditService auditService;

    private JTable logTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> entityTypeCombo;
    private JTextField userIdFilterField;
    private JTextArea detailsArea;
    private JButton refreshButton;
    private JLabel countLabel;

    public AuditLogPanel(User currentUser) {
        this(currentUser, new AuditService());
    }

    public AuditLogPanel(User currentUser, AuditService auditService) {
        this.currentUser = currentUser;
        this.auditService = auditService;
        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setOpaque(false);

        JLabel titleLabel = new JLabel("System Security & Audit Trail");
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);

        filters.add(new JLabel("Entity Type:"));
        entityTypeCombo = new JComboBox<>(new String[]{
                "ALL", "USER", "STUDENT", "FACULTY", "DEPARTMENT", "COURSE",
                "COURSE_ASSIGNMENT", "ENROLLMENT", "ATTENDANCE", "EXAMINATION",
                "MARK", "FEE_STRUCTURE", "FEE_PAYMENT"
        });
        entityTypeCombo.addActionListener(e -> loadData());
        filters.add(entityTypeCombo);

        filters.add(new JLabel("User ID:"));
        userIdFilterField = new JTextField(5);
        userIdFilterField.addActionListener(e -> loadData());
        filters.add(userIdFilterField);

        refreshButton = SwingUtils.createSecondaryButton("Refresh");
        refreshButton.addActionListener(e -> loadData());
        filters.add(refreshButton);

        topBar.add(filters, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Center SplitPane: Table on Top, Details Inspector on Bottom
        String[] columns = {"Log ID", "Timestamp", "User ID", "Username", "Action", "Entity Type", "Entity ID"};
        tableModel = new DefaultTableModel(columns, 0);
        logTable = SwingUtils.createStandardTable(tableModel);

        detailsArea = new JTextArea(4, 40);
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        detailsArea.setBackground(new Color(250, 252, 255));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && logTable.getSelectedRow() != -1) {
                int row = logTable.getSelectedRow();
                String details = (String) logTable.getClientProperty("details_" + row);
                detailsArea.setText(details != null ? details : "No change payload recorded.");
            }
        });

        JScrollPane tableScroll = new JScrollPane(logTable);
        JPanel bottomCard = SwingUtils.createCardPanel("Audit Entry Payload (Old / New Values)");
        bottomCard.add(new JScrollPane(detailsArea), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, bottomCard);
        splitPane.setResizeWeight(0.7);
        splitPane.setDividerLocation(380);
        add(splitPane, BorderLayout.CENTER);

        // South Status
        countLabel = new JLabel("Audit log records loaded.");
        countLabel.setFont(SwingUtils.SMALL_FONT);
        add(countLabel, BorderLayout.SOUTH);
    }

    public void loadData() {
        String entityType = (String) entityTypeCombo.getSelectedItem();
        String userIdStr = userIdFilterField.getText().trim();
        refreshButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> {
                    if (!userIdStr.isEmpty()) {
                        try {
                            int uid = Integer.parseInt(userIdStr);
                            return auditService.getAuditLogsByUserId(currentUser, uid);
                        } catch (NumberFormatException ex) {
                            // ignore and fallback
                        }
                    }
                    if (entityType != null && !"ALL".equalsIgnoreCase(entityType)) {
                        return auditService.getAuditLogsByEntityType(currentUser, entityType);
                    }
                    return auditService.getAllAuditLogs(currentUser);
                },
                logs -> {
                    tableModel.setRowCount(0);
                    int idx = 0;
                    for (AuditLog l : logs) {
                        tableModel.addRow(new Object[]{
                                l.getId(),
                                l.getTimestamp(),
                                l.getUserId(),
                                l.getUsername() != null ? l.getUsername() : "—",
                                l.getAction(),
                                l.getEntityType(),
                                l.getEntityId()
                        });
                        String payload = "Old Value: " + (l.getOldValue() != null ? l.getOldValue() : "—") +
                                "\nNew Value: " + (l.getNewValue() != null ? l.getNewValue() : "—");
                        logTable.putClientProperty("details_" + idx, payload);
                        idx++;
                    }
                    detailsArea.setText("");
                    countLabel.setText("Total Audit Entries: " + logs.size());
                    refreshButton.setEnabled(true);
                },
                error -> {
                    refreshButton.setEnabled(true);
                    countLabel.setText("Error loading logs: " + error.getMessage());
                }
        );
    }
}
