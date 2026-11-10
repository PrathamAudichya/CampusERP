package com.college.ui.panel;

import com.college.dao.StudentDAO;
import com.college.model.FeePayment;
import com.college.model.FeeStructure;
import com.college.model.InstitutionBankDetails;
import com.college.model.Role;
import com.college.model.Student;
import com.college.model.User;
import com.college.service.BankDetailsService;
import com.college.service.FeePaymentService;
import com.college.service.FeeStructureService;
import com.college.service.PaymentGatewayService;
import com.college.service.StudentService;
import com.college.ui.theme.CampusTheme;
import com.college.ui.util.SwingUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fee payment collection, balance verification, and refund management panel with exact integer paise accounting.
 */
public class FeePaymentPanel extends JPanel {

    private final User currentUser;
    private final FeePaymentService feePaymentService;
    private final FeeStructureService feeStructureService;
    private final StudentService studentService;
    private final StudentDAO studentDAO;
    private final PaymentGatewayService gatewayService;
    private final BankDetailsService bankDetailsService;

    private JTable paymentTable;
    private DefaultTableModel tableModel;

    // Controls for Admin
    private JComboBox<StudentItem> studentCombo;
    private JComboBox<StructureItem> structureCombo;
    private JTextField amountField;
    private JComboBox<String> methodCombo;
    private JTextField txnRefField;
    private JTextField paymentDateField;
    private JLabel balanceLabel;
    private JButton checkBalanceButton;
    private JButton payButton;
    private JButton refundButton;
    private JButton refreshButton;
    private JButton viewReceiptButton;
    private JButton configBankBtn;

    // Controls for Student
    private JLabel studentDuesSummaryLabel;
    private JButton payOnlineButton;
    private JButton submitUtrButton;
    private JButton viewBankInfoButton;
    private List<FeeStructure> studentApplicableStructures = new ArrayList<>();

    private Integer selectedPaymentId = null;
    private final Map<Integer, FeeStructure> structureMap = new HashMap<>();

    public FeePaymentPanel(User currentUser) {
        this(currentUser, new FeePaymentService(), new FeeStructureService(), new StudentService(),
                new StudentDAO(), new PaymentGatewayService(), new BankDetailsService());
    }

    public FeePaymentPanel(User currentUser, FeePaymentService feePaymentService,
                           FeeStructureService feeStructureService, StudentService studentService,
                           StudentDAO studentDAO) {
        this(currentUser, feePaymentService, feeStructureService, studentService, studentDAO,
                new PaymentGatewayService(), new BankDetailsService());
    }

    public FeePaymentPanel(User currentUser, FeePaymentService feePaymentService,
                           FeeStructureService feeStructureService, StudentService studentService,
                           StudentDAO studentDAO, PaymentGatewayService gatewayService,
                           BankDetailsService bankDetailsService) {
        this.currentUser = currentUser;
        this.feePaymentService = feePaymentService;
        this.feeStructureService = feeStructureService;
        this.studentService = studentService;
        this.studentDAO = studentDAO;
        this.gatewayService = gatewayService;
        this.bankDetailsService = bankDetailsService;
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(12, 12));
        setBackground(SwingUtils.BG_LIGHT);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout(8, 8));
        topBar.setOpaque(false);

        String title = (currentUser.getRole() == Role.ADMIN) ? "Fee Payments & Refund Processing" : "My Fee Payments & Ledger";
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(SwingUtils.TITLE_FONT);
        titleLabel.setForeground(SwingUtils.PRIMARY_COLOR);
        topBar.add(titleLabel, BorderLayout.WEST);

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topActions.setOpaque(false);

        if (currentUser.getRole() == Role.ADMIN) {
            configBankBtn = SwingUtils.createSecondaryButton("Bank Details Config");
            configBankBtn.setToolTipText("Configure institutional bank and UPI payment instructions");
            configBankBtn.addActionListener(e -> showConfigureBankDetailsDialog());
            topActions.add(configBankBtn);
        } else if (currentUser.getRole() == Role.STUDENT) {
            viewBankInfoButton = SwingUtils.createSecondaryButton("Institution Bank & UPI");
            viewBankInfoButton.addActionListener(e -> showBankDetailsDialog());
            topActions.add(viewBankInfoButton);
        }

        viewReceiptButton = SwingUtils.createSecondaryButton("View Receipt");
        viewReceiptButton.setEnabled(false);
        viewReceiptButton.addActionListener(e -> showReceiptDialog());
        topActions.add(viewReceiptButton);

        refreshButton = SwingUtils.createSecondaryButton("Refresh Ledger");
        refreshButton.addActionListener(e -> loadData());
        topActions.add(refreshButton);

        topBar.add(topActions, BorderLayout.EAST);

        add(topBar, BorderLayout.NORTH);

        // Center Table
        String[] columns = {"Payment ID", "Student ID", "Fee Type", "Amount (INR)", "Date", "Method", "Status", "Reference", "Original Ref ID"};
        tableModel = new DefaultTableModel(columns, 0);
        paymentTable = SwingUtils.createStandardTable(tableModel);

        paymentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && paymentTable.getSelectedRow() != -1) {
                int row = paymentTable.getSelectedRow();
                selectedPaymentId = (Integer) tableModel.getValueAt(row, 0);
                String status = (String) tableModel.getValueAt(row, 6);
                if (refundButton != null) {
                    refundButton.setEnabled("COMPLETED".equalsIgnoreCase(status));
                }
                if (viewReceiptButton != null) {
                    viewReceiptButton.setEnabled(true);
                }
            } else if (paymentTable.getSelectedRow() == -1 && viewReceiptButton != null) {
                viewReceiptButton.setEnabled(false);
            }
        });

        JScrollPane scrollPane = new JScrollPane(paymentTable);
        add(scrollPane, BorderLayout.CENTER);

        // South Section (Admin Actions: Payment & Refund)
        if (currentUser.getRole() == Role.ADMIN) {
            JPanel southContainer = new JPanel(new BorderLayout(8, 8));
            southContainer.setOpaque(false);

            JPanel payCard = SwingUtils.createCardPanel("Collect Student Fee Payment");
            JPanel formGrid = new JPanel(new GridLayout(2, 4, 8, 8));
            formGrid.setOpaque(false);

            studentCombo = new JComboBox<>();
            studentCombo.addActionListener(e -> updateBalanceDisplay());
            structureCombo = new JComboBox<>();
            structureCombo.addActionListener(e -> updateBalanceDisplay());

            amountField = new JTextField("10000.00");
            methodCombo = new JComboBox<>(new String[]{"ONLINE", "CASH", "CHEQUE"});
            txnRefField = new JTextField("TXN-" + System.currentTimeMillis() % 1000000);
            paymentDateField = new JTextField(LocalDate.now().toString());

            formGrid.add(createFieldBox("Student:", studentCombo));
            formGrid.add(createFieldBox("Fee Structure:", structureCombo));
            formGrid.add(createFieldBox("Amount in INR (e.g. 10000):", amountField));
            formGrid.add(createFieldBox("Payment Method:", methodCombo));

            formGrid.add(createFieldBox("Payment Date (YYYY-MM-DD):", paymentDateField));
            formGrid.add(createFieldBox("Transaction Reference:", txnRefField));

            balanceLabel = new JLabel("Balance: —");
            balanceLabel.setFont(SwingUtils.BOLD_FONT);
            balanceLabel.setForeground(SwingUtils.DANGER_COLOR);
            JPanel balBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 16));
            balBox.setOpaque(false);
            balBox.add(balanceLabel);
            formGrid.add(balBox);

            checkBalanceButton = SwingUtils.createSecondaryButton("Check Balance");
            checkBalanceButton.addActionListener(e -> updateBalanceDisplay());
            JPanel chkBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 12));
            chkBox.setOpaque(false);
            chkBox.add(checkBalanceButton);
            formGrid.add(chkBox);

            JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonRow.setOpaque(false);

            refundButton = SwingUtils.createSecondaryButton("Refund Selected Payment");
            refundButton.setEnabled(false);
            refundButton.addActionListener(e -> promptRefundDialog());

            payButton = SwingUtils.createPrimaryButton("Process Payment");
            payButton.addActionListener(e -> processPayment());

            buttonRow.add(refundButton);
            buttonRow.add(payButton);

            payCard.add(formGrid, BorderLayout.CENTER);
            payCard.add(buttonRow, BorderLayout.SOUTH);

            southContainer.add(payCard, BorderLayout.CENTER);
            add(southContainer, BorderLayout.SOUTH);

            loadAdminCatalogs();
        } else if (currentUser.getRole() == Role.STUDENT) {
            JPanel southContainer = new JPanel(new BorderLayout(8, 8));
            southContainer.setOpaque(false);

            JPanel studentCard = SwingUtils.createCardPanel("Student Fee Actions & Payment Gateway");
            studentDuesSummaryLabel = new JLabel("Loading fee demands and ledger status...");
            studentDuesSummaryLabel.setFont(CampusTheme.FONT_BODY);
            studentDuesSummaryLabel.setForeground(CampusTheme.TEXT_SECONDARY);
            studentCard.add(studentDuesSummaryLabel, BorderLayout.CENTER);

            JPanel studentBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
            studentBtnBar.setOpaque(false);

            payOnlineButton = SwingUtils.createPrimaryButton("Pay Online (Razorpay / PayU Sandbox)");
            payOnlineButton.addActionListener(e -> showOnlinePaymentDialog());
            studentBtnBar.add(payOnlineButton);

            submitUtrButton = SwingUtils.createSecondaryButton("Submit Bank Transfer (UTR)");
            submitUtrButton.addActionListener(e -> showBankTransferDialog());
            studentBtnBar.add(submitUtrButton);

            studentCard.add(studentBtnBar, BorderLayout.SOUTH);
            southContainer.add(studentCard, BorderLayout.CENTER);
            add(southContainer, BorderLayout.SOUTH);
        }

        loadData();
    }

    private JPanel createFieldBox(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(2, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(labelText);
        l.setFont(SwingUtils.SMALL_FONT);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private void loadAdminCatalogs() {
        SwingUtils.executeAsync(
                this,
                () -> {
                    List<Student> students = studentService.getAllActiveStudents(currentUser);
                    List<FeeStructure> structures = feeStructureService.getAllActiveFeeStructures(currentUser);
                    return new Object[]{students, structures};
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<Student> students = (List<Student>) result[0];
                    @SuppressWarnings("unchecked")
                    List<FeeStructure> structures = (List<FeeStructure>) result[1];

                    studentCombo.removeAllItems();
                    for (Student s : students) {
                        studentCombo.addItem(new StudentItem(s.getId(), s.getFirstName() + " " + s.getLastName() + " (" + s.getStudentId() + ")"));
                    }

                    structureCombo.removeAllItems();
                    structureMap.clear();
                    for (FeeStructure fs : structures) {
                        structureMap.put(fs.getId(), fs);
                        structureCombo.addItem(new StructureItem(fs.getId(), fs.getFeeType() + " (" + fs.getAcademicYear() + ") — " + SwingUtils.formatPaise(fs.getAmountPaise())));
                    }

                    updateBalanceDisplay();
                }
        );
    }

    private void updateBalanceDisplay() {
        if (studentCombo == null || structureCombo == null) return;
        StudentItem student = (StudentItem) studentCombo.getSelectedItem();
        StructureItem structure = (StructureItem) structureCombo.getSelectedItem();
        if (student == null || structure == null) {
            if (balanceLabel != null) balanceLabel.setText("Balance: —");
            return;
        }

        SwingUtils.executeAsync(
                this,
                () -> feePaymentService.getOutstandingBalance(currentUser, student.id, structure.id),
                outstandingPaise -> {
                    if (balanceLabel != null) {
                        balanceLabel.setText("Outstanding: " + SwingUtils.formatPaise(outstandingPaise));
                        balanceLabel.setForeground(outstandingPaise > 0 ? SwingUtils.DANGER_COLOR : SwingUtils.SUCCESS_COLOR);
                    }
                }
        );
    }

    public void loadData() {
        refreshButton.setEnabled(false);
        SwingUtils.executeAsync(
                this,
                () -> {
                    if (currentUser.getRole() == Role.STUDENT) {
                        Student s = studentDAO.findByUserId(currentUser.getId());
                        if (s == null) return new Object[]{List.<FeePayment>of(), List.<FeeStructure>of(), 0L, 0L, 0L};
                        List<FeePayment> history = feePaymentService.getPaymentHistoryByStudent(currentUser, s.getId());
                        List<FeeStructure> allStructures = feeStructureService.getAllActiveFeeStructures(currentUser);
                        long totalDemanded = 0;
                        long totalOutstanding = 0;
                        List<FeeStructure> studentStructures = new ArrayList<>();
                        for (FeeStructure fs : allStructures) {
                            if (fs.getDepartmentId() <= 0 || fs.getDepartmentId() == s.getDepartmentId()) {
                                studentStructures.add(fs);
                                totalDemanded += fs.getAmountPaise();
                                long bal = feePaymentService.getOutstandingBalance(currentUser, s.getId(), fs.getId());
                                totalOutstanding += bal;
                            }
                        }
                        long totalPaid = totalDemanded - totalOutstanding;
                        if (totalPaid < 0) totalPaid = 0;
                        return new Object[]{history, studentStructures, totalDemanded, totalPaid, totalOutstanding};
                    } else {
                        StudentItem sel = (studentCombo != null) ? (StudentItem) studentCombo.getSelectedItem() : null;
                        List<FeePayment> history = (sel != null && sel.id > 0)
                                ? feePaymentService.getPaymentHistoryByStudent(currentUser, sel.id)
                                : List.<FeePayment>of();
                        return new Object[]{history, List.<FeeStructure>of(), 0L, 0L, 0L};
                    }
                },
                result -> {
                    @SuppressWarnings("unchecked")
                    List<FeePayment> payments = (List<FeePayment>) result[0];
                    @SuppressWarnings("unchecked")
                    List<FeeStructure> structs = (List<FeeStructure>) result[1];
                    long totalDemanded = (Long) result[2];
                    long totalPaid = (Long) result[3];
                    long totalOutstanding = (Long) result[4];

                    studentApplicableStructures.clear();
                    studentApplicableStructures.addAll(structs);

                    if (studentDuesSummaryLabel != null) {
                        if (structs.isEmpty()) {
                            studentDuesSummaryLabel.setText("No fee structures assigned for your department / academic year.");
                            studentDuesSummaryLabel.setForeground(CampusTheme.TEXT_MUTED);
                        } else {
                            studentDuesSummaryLabel.setText(String.format(
                                    "Total Demanded: %s  |  Total Paid: %s  |  Outstanding Dues: %s",
                                    SwingUtils.formatPaise(totalDemanded),
                                    SwingUtils.formatPaise(totalPaid),
                                    SwingUtils.formatPaise(totalOutstanding)
                            ));
                            studentDuesSummaryLabel.setForeground(totalOutstanding > 0 ? SwingUtils.DANGER_COLOR : SwingUtils.SUCCESS_COLOR);
                        }
                    }

                    tableModel.setRowCount(0);
                    for (FeePayment p : payments) {
                        String feeType = "Fee #" + p.getFeeStructureId();
                        FeeStructure fs = structureMap.get(p.getFeeStructureId());
                        if (fs != null) feeType = fs.getFeeType();

                        tableModel.addRow(new Object[]{
                                p.getId(),
                                p.getStudentId(),
                                feeType,
                                SwingUtils.formatPaise(p.getAmountPaidPaise()),
                                p.getPaymentDate(),
                                p.getPaymentMethod(),
                                p.getStatus(),
                                p.getTransactionRef(),
                                p.getOriginalPaymentId() != null ? String.valueOf(p.getOriginalPaymentId()) : "—"
                        });
                    }
                    if (refundButton != null) refundButton.setEnabled(false);
                    refreshButton.setEnabled(true);
                },
                error -> refreshButton.setEnabled(true)
        );
    }

    private void processPayment() {
        StudentItem student = (StudentItem) studentCombo.getSelectedItem();
        StructureItem structure = (StructureItem) structureCombo.getSelectedItem();
        String amtStr = amountField.getText().trim();
        String method = (String) methodCombo.getSelectedItem();
        String dateStr = paymentDateField.getText().trim();
        String txnRef = txnRefField.getText().trim();

        if (student == null || structure == null || amtStr.isEmpty() || dateStr.isEmpty()) {
            SwingUtils.showWarning(this, "Please fill in all payment fields.");
            return;
        }

        long paise;
        try {
            paise = SwingUtils.parsePaise(amtStr);
        } catch (Exception ex) {
            SwingUtils.showWarning(this, ex.getMessage());
            return;
        }

        FeeStructure fs = structureMap.get(structure.id);
        String academicYear = (fs != null) ? fs.getAcademicYear() : "2026-2027";
        payButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> feePaymentService.processPayment(currentUser, student.id, structure.id, academicYear, paise, method),
                payment -> {
                    payButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Payment processed successfully. Transaction Ref: " + payment.getTransactionRef());
                    txnRefField.setText("TXN-" + System.currentTimeMillis() % 1000000);
                    updateBalanceDisplay();
                    loadData();
                },
                error -> payButton.setEnabled(true)
        );
    }

    private void promptRefundDialog() {
        if (selectedPaymentId == null) return;
        String amtStr = JOptionPane.showInputDialog(this,
                "Enter refund amount in INR (e.g. 5000.00):",
                "Process Refund",
                JOptionPane.QUESTION_MESSAGE);
        if (amtStr == null || amtStr.trim().isEmpty()) return;

        long refundPaise;
        try {
            refundPaise = SwingUtils.parsePaise(amtStr);
        } catch (Exception ex) {
            SwingUtils.showWarning(this, ex.getMessage());
            return;
        }

        String reason = JOptionPane.showInputDialog(this, "Enter reason for refund:", "Refund Reason", JOptionPane.PLAIN_MESSAGE);
        if (reason == null) reason = "Administrative Refund";

        final String finalReason = reason;
        refundButton.setEnabled(false);

        SwingUtils.executeAsync(
                this,
                () -> feePaymentService.processRefund(currentUser, selectedPaymentId, refundPaise, finalReason),
                refund -> {
                    refundButton.setEnabled(true);
                    SwingUtils.showInfo(this, "Refund processed successfully. Reference: " + refund.getTransactionRef());
                    updateBalanceDisplay();
                    loadData();
                },
                error -> refundButton.setEnabled(true)
        );
    }

    private void showReceiptDialog() {
        int row = paymentTable.getSelectedRow();
        if (row == -1) return;
        int paymentId = (Integer) tableModel.getValueAt(row, 0);
        Object stuIdObj = tableModel.getValueAt(row, 1);
        String feeType = String.valueOf(tableModel.getValueAt(row, 2));
        String amount = String.valueOf(tableModel.getValueAt(row, 3));
        String date = String.valueOf(tableModel.getValueAt(row, 4));
        String method = String.valueOf(tableModel.getValueAt(row, 5));
        String status = String.valueOf(tableModel.getValueAt(row, 6));
        String ref = String.valueOf(tableModel.getValueAt(row, 7));

        Window parent = SwingUtilities.getWindowAncestor(this);
        FeeReceiptDialog dlg = new FeeReceiptDialog(parent, paymentId, String.valueOf(stuIdObj), feeType, amount, date, method, status, ref);
        dlg.setVisible(true);
    }

    private void showConfigureBankDetailsDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(parent, "Institutional Bank & Payment Account Configuration", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(560, 480);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(12, 12));

        InstitutionBankDetails currentDetails = bankDetailsService.getBankDetails(currentUser);

        JPanel p = new JPanel(new GridLayout(7, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JTextField beneficiaryField = new JTextField(currentDetails != null ? currentDetails.getBeneficiaryName() : "");
        JTextField bankField = new JTextField(currentDetails != null ? currentDetails.getBankName() : "");
        JTextField branchField = new JTextField(currentDetails != null ? currentDetails.getBranchName() : "");
        JTextField accountField = new JTextField(currentDetails != null ? currentDetails.getAccountNumber() : "");
        JTextField ifscField = new JTextField(currentDetails != null ? currentDetails.getIfscCode() : "");
        JTextField upiField = new JTextField(currentDetails != null ? currentDetails.getUpiId() : "");
        JTextField instructionsField = new JTextField(currentDetails != null ? currentDetails.getInstructions() : "");

        p.add(new JLabel("Beneficiary Name:")); p.add(beneficiaryField);
        p.add(new JLabel("Bank Name:")); p.add(bankField);
        p.add(new JLabel("Branch Name:")); p.add(branchField);
        p.add(new JLabel("Account Number:")); p.add(accountField);
        p.add(new JLabel("IFSC Code:")); p.add(ifscField);
        p.add(new JLabel("Institutional UPI ID:")); p.add(upiField);
        p.add(new JLabel("Payment Instructions:")); p.add(instructionsField);

        dlg.add(p, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancelBtn = SwingUtils.createSecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());
        JButton saveBtn = SwingUtils.createPrimaryButton("Save Bank Details");
        saveBtn.addActionListener(e -> {
            try {
                InstitutionBankDetails updated = new InstitutionBankDetails(
                        beneficiaryField.getText().trim(),
                        bankField.getText().trim(),
                        branchField.getText().trim(),
                        accountField.getText().trim(),
                        ifscField.getText().trim(),
                        "Current Account",
                        upiField.getText().trim(),
                        instructionsField.getText().trim()
                );
                bankDetailsService.updateBankDetails(currentUser, updated);
                SwingUtils.showInfo(dlg, "Institutional bank details updated successfully.");
                dlg.dispose();
            } catch (Exception ex) {
                SwingUtils.showWarning(dlg, ex.getMessage());
            }
        });
        btnBar.add(cancelBtn);
        btnBar.add(saveBtn);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showBankDetailsDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(parent, "College Bank & UPI Payment Details", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(520, 420);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(12, 12));

        InstitutionBankDetails details = bankDetailsService.getBankDetails(currentUser);

        JPanel p = new JPanel(new GridLayout(7, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        p.add(new JLabel("Beneficiary Account:"));
        p.add(new JLabel(details.getBeneficiaryName()));
        p.add(new JLabel("Bank Name:"));
        p.add(new JLabel(details.getBankName()));
        p.add(new JLabel("Branch:"));
        p.add(new JLabel(details.getBranchName()));
        p.add(new JLabel("Account Number:"));
        p.add(new JLabel(details.getAccountNumber()));
        p.add(new JLabel("IFSC Code:"));
        p.add(new JLabel(details.getIfscCode()));
        p.add(new JLabel("UPI ID / VPA:"));
        p.add(new JLabel(details.getUpiId()));
        p.add(new JLabel("Instructions:"));
        JLabel instLbl = new JLabel("<html>" + details.getInstructions() + "</html>");
        p.add(instLbl);

        dlg.add(p, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton copyBtn = SwingUtils.createSecondaryButton("Copy Details");
        copyBtn.addActionListener(e -> {
            String text = String.format(
                    "Beneficiary: %s\nBank: %s, %s\nAccount No: %s\nIFSC: %s\nUPI: %s\nInstructions: %s\n",
                    details.getBeneficiaryName(), details.getBankName(), details.getBranchName(),
                    details.getAccountNumber(), details.getIfscCode(), details.getUpiId(), details.getInstructions()
            );
            java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            SwingUtils.showInfo(dlg, "Bank details copied to clipboard.");
        });
        JButton closeBtn = SwingUtils.createPrimaryButton("Close");
        closeBtn.addActionListener(e -> dlg.dispose());
        btnBar.add(copyBtn);
        btnBar.add(closeBtn);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showOnlinePaymentDialog() {
        if (studentApplicableStructures.isEmpty()) {
            SwingUtils.showWarning(this, "No fee structures available for online payment.");
            return;
        }

        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(parent, "Online Fee Payment Gateway (Razorpay / PayU Sandbox)", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(560, 450);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(14, 14));

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JComboBox<StructureItem> structCombo = new JComboBox<>();
        for (FeeStructure fs : studentApplicableStructures) {
            structCombo.addItem(new StructureItem(fs.getId(), fs.getFeeType() + " (" + fs.getAcademicYear() + ") — " + SwingUtils.formatPaise(fs.getAmountPaise())));
        }

        JComboBox<String> gwCombo = new JComboBox<>(new String[]{"Razorpay Sandbox", "PayU Sandbox"});
        JTextField amtField = new JTextField();
        JLabel orderInfoLbl = new JLabel("Click 'Checkout' below");
        orderInfoLbl.setForeground(CampusTheme.TEXT_MUTED);

        Runnable syncAmt = () -> {
            StructureItem sel = (StructureItem) structCombo.getSelectedItem();
            if (sel != null) {
                FeeStructure fs = studentApplicableStructures.stream().filter(f -> f.getId() == sel.id).findFirst().orElse(null);
                if (fs != null) {
                    amtField.setText(String.format("%.2f", fs.getAmountPaise() / 100.0));
                }
            }
        };
        structCombo.addActionListener(e -> syncAmt.run());
        syncAmt.run();

        form.add(new JLabel("Select Fee Structure:")); form.add(structCombo);
        form.add(new JLabel("Payment Gateway:")); form.add(gwCombo);
        form.add(new JLabel("Amount to Pay (INR):")); form.add(amtField);
        form.add(new JLabel("Gateway Order Status:")); form.add(orderInfoLbl);

        dlg.add(form, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancelBtn = SwingUtils.createSecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());

        JButton checkoutBtn = SwingUtils.createPrimaryButton("Proceed to Secure Gateway Checkout");
        checkoutBtn.addActionListener(e -> {
            StructureItem sel = (StructureItem) structCombo.getSelectedItem();
            if (sel == null) return;
            String gw = ((String) gwCombo.getSelectedItem()).startsWith("PayU") ? "PAYU" : "RAZORPAY";
            long paise;
            try {
                paise = SwingUtils.parsePaise(amtField.getText().trim());
            } catch (Exception ex) {
                SwingUtils.showWarning(dlg, ex.getMessage());
                return;
            }

            try {
                PaymentGatewayService.PaymentOrder order = gatewayService.createPaymentOrder(currentUser, sel.id, gw, paise);
                String mockPaymentId = ("PAYU".equals(gw) ? "payu_pay_" : "pay_") + System.currentTimeMillis();
                String signature = PaymentGatewayService.generateSandboxSignature(order.getOrderId(), mockPaymentId, gw);

                int confirm = JOptionPane.showConfirmDialog(dlg,
                        "Gateway Checkout Initialized!\n" +
                        "Order ID: " + order.getOrderId() + "\n" +
                        "Gateway: " + gw + "\n" +
                        "Amount: ₹" + (paise / 100.0) + "\n\n" +
                        "Authorize sandbox transaction and verify HMAC signature?",
                        "Gateway Simulator",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (confirm == JOptionPane.YES_OPTION) {
                    FeePayment payment = gatewayService.processVerifiedPayment(currentUser, order, mockPaymentId, signature);
                    SwingUtils.showInfo(dlg, "Payment successful!\nReceipt No: " + payment.getReceiptNumber() + "\nTxn Ref: " + payment.getTransactionRef());
                    dlg.dispose();
                    loadData();
                }
            } catch (Exception ex) {
                SwingUtils.showWarning(dlg, "Payment Error: " + ex.getMessage());
            }
        });

        btnBar.add(cancelBtn);
        btnBar.add(checkoutBtn);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showBankTransferDialog() {
        if (studentApplicableStructures.isEmpty()) {
            SwingUtils.showWarning(this, "No fee structures available to pay against.");
            return;
        }

        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(parent, "Submit Bank Transfer / NEFT / IMPS / UPI Receipt (UTR)", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(520, 420);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(14, 14));

        JPanel form = new JPanel(new GridLayout(4, 2, 10, 10));
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JComboBox<StructureItem> structCombo = new JComboBox<>();
        for (FeeStructure fs : studentApplicableStructures) {
            structCombo.addItem(new StructureItem(fs.getId(), fs.getFeeType() + " (" + fs.getAcademicYear() + ") — " + SwingUtils.formatPaise(fs.getAmountPaise())));
        }

        JTextField amtField = new JTextField();
        JTextField utrField = new JTextField();
        JTextField dateField = new JTextField(LocalDate.now().toString());

        Runnable syncAmt = () -> {
            StructureItem sel = (StructureItem) structCombo.getSelectedItem();
            if (sel != null) {
                FeeStructure fs = studentApplicableStructures.stream().filter(f -> f.getId() == sel.id).findFirst().orElse(null);
                if (fs != null) {
                    amtField.setText(String.format("%.2f", fs.getAmountPaise() / 100.0));
                }
            }
        };
        structCombo.addActionListener(e -> syncAmt.run());
        syncAmt.run();

        form.add(new JLabel("Select Fee Structure:")); form.add(structCombo);
        form.add(new JLabel("Transfer Amount (INR):")); form.add(amtField);
        form.add(new JLabel("Bank UTR / Transaction Ref:")); form.add(utrField);
        form.add(new JLabel("Transfer Date (YYYY-MM-DD):")); form.add(dateField);

        dlg.add(form, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancelBtn = SwingUtils.createSecondaryButton("Cancel");
        cancelBtn.addActionListener(e -> dlg.dispose());

        JButton submitBtn = SwingUtils.createPrimaryButton("Submit Transfer for Verification");
        submitBtn.addActionListener(e -> {
            StructureItem sel = (StructureItem) structCombo.getSelectedItem();
            if (sel == null) return;
            String utr = utrField.getText().trim();
            if (utr.length() < 6) {
                SwingUtils.showWarning(dlg, "Please provide a valid UTR number (at least 6 characters).");
                return;
            }
            long paise;
            try {
                paise = SwingUtils.parsePaise(amtField.getText().trim());
            } catch (Exception ex) {
                SwingUtils.showWarning(dlg, ex.getMessage());
                return;
            }

            try {
                FeePayment payment = gatewayService.submitManualBankTransfer(currentUser, sel.id, paise, utr, dateField.getText().trim());
                SwingUtils.showInfo(dlg, "Bank transfer submitted successfully!\nReceipt No: " + payment.getReceiptNumber() + "\nPending accounts verification.");
                dlg.dispose();
                loadData();
            } catch (Exception ex) {
                SwingUtils.showWarning(dlg, "Submission Error: " + ex.getMessage());
            }
        });

        btnBar.add(cancelBtn);
        btnBar.add(submitBtn);
        dlg.add(btnBar, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    public static class FeeReceiptDialog extends JDialog {
        public FeeReceiptDialog(Window parent, int paymentId, String studentId, String feeType,
                                String amount, String date, String method, String status, String ref) {
            super(parent, "Official Payment Receipt — REC-" + paymentId, ModalityType.APPLICATION_MODAL);
            setSize(480, 420);
            setLocationRelativeTo(parent);
            setResizable(false);
            setLayout(new BorderLayout(16, 16));
            getContentPane().setBackground(CampusTheme.BG_CARD);

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));

            JLabel title = new JLabel("CAMPUS ERP — FEE PAYMENT RECEIPT");
            title.setFont(SwingUtils.TITLE_FONT);
            title.setForeground(SwingUtils.PRIMARY_COLOR);
            header.add(title, BorderLayout.NORTH);

            JLabel subtitle = new JLabel("Official Student Financial Transaction Record");
            subtitle.setFont(SwingUtils.SMALL_FONT);
            subtitle.setForeground(SwingUtils.MUTED_COLOR);
            header.add(subtitle, BorderLayout.SOUTH);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(7, 2, 8, 8));
            grid.setOpaque(false);
            grid.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));

            grid.add(new JLabel("Receipt Number:"));
            grid.add(new JLabel("REC-" + paymentId + "-" + ref));
            grid.add(new JLabel("Student Reference:"));
            grid.add(new JLabel(studentId));
            grid.add(new JLabel("Fee Classification:"));
            grid.add(new JLabel(feeType));
            grid.add(new JLabel("Amount Paid:"));
            grid.add(new JLabel("INR " + amount));
            grid.add(new JLabel("Payment Date:"));
            grid.add(new JLabel(date));
            grid.add(new JLabel("Payment Method:"));
            grid.add(new JLabel(method));
            grid.add(new JLabel("Transaction Status:"));
            JLabel stLabel = new JLabel(status);
            stLabel.setForeground("COMPLETED".equalsIgnoreCase(status) ? SwingUtils.SUCCESS_COLOR : SwingUtils.DANGER_COLOR);
            grid.add(stLabel);
            add(grid, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
            bottom.setOpaque(false);
            JButton copyBtn = SwingUtils.createSecondaryButton("Copy to Clipboard");
            copyBtn.addActionListener(e -> {
                String receiptText = String.format(
                        "========================================\n" +
                        "      CAMPUS ERP - PAYMENT RECEIPT      \n" +
                        "========================================\n" +
                        "Receipt No   : REC-%d-%s\n" +
                        "Student Ref  : %s\n" +
                        "Fee Type     : %s\n" +
                        "Amount Paid  : INR %s\n" +
                        "Payment Date : %s\n" +
                        "Method       : %s\n" +
                        "Txn Ref      : %s\n" +
                        "Status       : %s\n" +
                        "========================================\n" +
                        "Generated by Campus ERP Institutional System\n",
                        paymentId, ref, studentId, feeType, amount, date, method, ref, status
                );
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(receiptText);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                SwingUtils.showInfo(this, "Receipt copied to clipboard.");
            });
            JButton closeBtn = SwingUtils.createPrimaryButton("Close");
            closeBtn.addActionListener(e -> dispose());
            bottom.add(copyBtn);
            bottom.add(closeBtn);
            add(bottom, BorderLayout.SOUTH);
        }
    }

    public static class StudentItem {
        public final int id;
        public final String name;
        public StudentItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static class StructureItem {
        public final int id;
        public final String name;
        public StructureItem(int id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }
}
