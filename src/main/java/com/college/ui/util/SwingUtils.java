package com.college.ui.util;

import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.ui.components.CampusIcons;
import com.college.ui.components.DangerButton;
import com.college.ui.components.PrimaryButton;
import com.college.ui.components.SecondaryButton;
import com.college.ui.components.SuccessButton;
import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Thread-safe Swing utilities, EDT-safe SwingWorker helpers, dialog dispatchers,
 * high-contrast accessible button creators, and currency/formatting routines for Campus ERP.
 */
public final class SwingUtils {

    public static final Font TITLE_FONT = CampusTheme.FONT_TITLE;
    public static final Font SUBTITLE_FONT = CampusTheme.FONT_SUBTITLE;
    public static final Font HEADER_FONT = CampusTheme.FONT_HEADING;
    public static final Font BOLD_FONT = CampusTheme.FONT_BOLD;
    public static final Font REGULAR_FONT = CampusTheme.FONT_BODY;
    public static final Font SMALL_FONT = CampusTheme.FONT_SMALL;

    public static final Color PRIMARY_COLOR = CampusTheme.PRIMARY;
    public static final Color ACCENT_COLOR = CampusTheme.TERRACOTTA;
    public static final Color BG_LIGHT = CampusTheme.BG_MAIN;
    public static final Color TEXT_DARK = CampusTheme.TEXT_PRIMARY;
    public static final Color SUCCESS_COLOR = CampusTheme.SUCCESS;
    public static final Color DANGER_COLOR = CampusTheme.ERROR;
    public static final Color WARNING_COLOR = CampusTheme.WARNING;
    public static final Color MUTED_COLOR = CampusTheme.TEXT_MUTED;

    private SwingUtils() {
        // Utility class
    }

    /**
     * Executes a background task asynchronously via SwingWorker and dispatches
     * results or domain-specific exceptions to the Event Dispatch Thread (EDT).
     *
     * @param parent         parent component for cursor and dialog positioning
     * @param backgroundTask callable representing the service or DB operation
     * @param onSuccess      callback invoked on EDT upon successful completion
     * @param onError        optional callback invoked on EDT upon error
     * @param <T>            return type of background task
     */
    public static <T> void executeAsync(
            Component parent,
            Callable<T> backgroundTask,
            Consumer<T> onSuccess,
            Consumer<Throwable> onError) {

        final Component root = (parent != null) ? SwingUtilities.getRoot(parent) : null;
        if (root != null) {
            root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return backgroundTask.call();
            }

            @Override
            protected void done() {
                // Guaranteed to run on the Swing Event Dispatch Thread (EDT)
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (onError != null) {
                        onError.accept(e);
                    } else {
                        showError(parent, "Operation was interrupted.");
                    }
                } catch (ExecutionException e) {
                    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                    System.err.println("[ERROR] Asynchronous execution failed: " + cause.getMessage());
                    cause.printStackTrace(System.err);

                    if (onError != null) {
                        onError.accept(cause);
                    } else {
                        if (cause instanceof ValidationException) {
                            showWarning(parent, cause.getMessage());
                        } else if (cause instanceof AuthenticationException) {
                            showError(parent, cause.getMessage());
                        } else {
                            showError(parent, getSafeUserErrorMessage(cause, "Operation failed"));
                        }
                    }
                } finally {
                    if (root != null) {
                        root.setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        }.execute();
    }

    /**
     * Overload for fire-and-forget or success-only asynchronous tasks.
     */
    public static <T> void executeAsync(
            Component parent,
            Callable<T> backgroundTask,
            Consumer<T> onSuccess) {
        executeAsync(parent, backgroundTask, onSuccess, null);
    }

    // =========================================================================
    // Dialog and Feedback Utilities
    // =========================================================================

    public static void showInfo(Component parent, String message) {
        if (GraphicsEnvironment.isHeadless() || parent == null) {
            System.out.println("[INFO] " + message);
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showInfo(parent, message));
            return;
        }
        JOptionPane.showMessageDialog(parent, message, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showWarning(Component parent, String message) {
        if (GraphicsEnvironment.isHeadless() || parent == null) {
            System.err.println("[WARN] " + message);
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showWarning(parent, message));
            return;
        }
        JOptionPane.showMessageDialog(parent, message, "Validation Warning", JOptionPane.WARNING_MESSAGE);
    }

    public static void showError(Component parent, String message) {
        if (GraphicsEnvironment.isHeadless() || parent == null) {
            System.err.println("[ERROR] " + message);
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showError(parent, message));
            return;
        }
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static boolean showConfirm(Component parent, String message, String title) {
        int opt = JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return opt == JOptionPane.YES_OPTION;
    }

    /**
     * Extracts a safe, sanitized user-facing error message from a Throwable.
     * Business validation and authentication exceptions preserve their clear user-directed messages.
     * Technical, SQL, and internal database exceptions are sanitized to prevent exposing sensitive internal
     * schema or system details, while retaining detailed diagnostics and stack traces in application logs.
     *
     * @param throwable      the error to inspect
     * @param defaultContext description of the attempted action (e.g., "Failed to update username")
     * @return safe user-facing message
     */
    public static String getSafeUserErrorMessage(Throwable throwable, String defaultContext) {
        if (throwable == null) {
            return defaultContext;
        }
        // Retain detailed error diagnostics in application logs
        System.err.println("[ERROR] Exception during " + defaultContext + ": " + throwable.getMessage());
        throwable.printStackTrace(System.err);

        if (throwable instanceof ValidationException || throwable instanceof AuthenticationException || throwable instanceof IllegalArgumentException) {
            String msg = throwable.getMessage();
            return (msg != null && !msg.isBlank()) ? msg : defaultContext;
        }

        if (throwable instanceof DataAccessException || (throwable.getCause() instanceof java.sql.SQLException)) {
            return defaultContext + ": A database error occurred. Please contact your system administrator.";
        }

        return defaultContext + ": An unexpected error occurred. Please try again or check application logs.";
    }

    // =========================================================================
    // Financial & Currency Utilities (INR Paise <-> Rupees via BigDecimal)
    // =========================================================================

    /**
     * Formats an integer amount in paise as Indian Rupees (INR) string.
     * E.g., 150000L -> "₹ 1,500.00"
     */
    public static String formatPaise(long paise) {
        boolean negative = paise < 0;
        long absPaise = Math.abs(paise);
        long rupees = absPaise / 100;
        long fraction = absPaise % 100;
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        String formattedRupees = nf.format(rupees);
        return (negative ? "-₹ " : "₹ ") + formattedRupees + String.format(".%02d", fraction);
    }

    /**
     * Parses a user-entered Indian Rupee string (e.g., "1,500.50" or "₹1500")
     * into exact integer paise using BigDecimal to eliminate floating-point error.
     *
     * @param inrText user input string
     * @return exact paise value
     * @throws ValidationException if the format is invalid or scale exceeds 2
     */
    public static long parsePaise(String inrText) {
        if (inrText == null || inrText.trim().isEmpty()) {
            throw new ValidationException("Amount cannot be empty.");
        }
        String cleaned = inrText.replace("₹", "").replace("INR", "").replace(",", "").trim();
        try {
            BigDecimal bd = new BigDecimal(cleaned);
            if (bd.scale() > 2) {
                throw new ValidationException("Amount cannot have more than 2 decimal places (paise).");
            }
            if (bd.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Amount cannot be negative.");
            }
            BigDecimal paiseBd = bd.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.UNNECESSARY);
            return paiseBd.longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new ValidationException("Invalid currency format: " + inrText + ". Expected e.g. 1500 or 1500.50");
        }
    }

    // =========================================================================
    // Table Styling & Helper Utilities
    // =========================================================================

    /**
     * Configures a JTable with standard row heights (32px), clean institutional headers (#ECEFE8),
     * alternating row backgrounds, and non-editable cells with guaranteed contrast.
     */
    public static JTable createStandardTable(TableModel model) {
        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFA, 0xFB, 0xF9));
                    c.setForeground(CampusTheme.TEXT_PRIMARY);
                } else {
                    c.setBackground(CampusTheme.SIDEBAR_ACTIVE);
                    c.setForeground(CampusTheme.PRIMARY);
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                }
                return c;
            }
        };
        table.setFont(CampusTheme.FONT_BODY);
        table.setRowHeight(32);
        table.getTableHeader().setFont(CampusTheme.FONT_HEADING);
        table.getTableHeader().setBackground(CampusTheme.BG_SIDEBAR);
        table.getTableHeader().setForeground(CampusTheme.TEXT_PRIMARY);
        table.getTableHeader().setPreferredSize(new Dimension(table.getTableHeader().getWidth(), 36));
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setGridColor(CampusTheme.BORDER_LIGHT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        return table;
    }

    /**
     * Wraps a component inside a clean titled or bordered JPanel with institutional card styling.
     */
    public static JPanel createCardPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(CampusTheme.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        if (title != null && !title.isEmpty()) {
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(CampusTheme.FONT_SUBTITLE);
            titleLabel.setForeground(CampusTheme.TEXT_PRIMARY);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
            panel.add(titleLabel, BorderLayout.NORTH);
        }
        return panel;
    }

    /**
     * Helper to create styled primary buttons with guaranteed contrast.
     */
    public static JButton createPrimaryButton(String text) {
        return new PrimaryButton(text);
    }

    /**
     * Helper to create styled secondary buttons with guaranteed contrast.
     */
    public static JButton createSecondaryButton(String text) {
        return new SecondaryButton(text);
    }

    /**
     * Helper to create styled danger buttons with guaranteed contrast.
     */
    public static JButton createDangerButton(String text) {
        return new DangerButton(text);
    }

    /**
     * Helper to create styled success buttons with guaranteed contrast.
     */
    public static JButton createSuccessButton(String text) {
        return new SuccessButton(text);
    }

    /**
     * Creates an institutional directory table with responsive horizontal scrolling,
     * cell tooltips showing untruncated content, column minimum/preferred/maximum widths,
     * cell alignments, and TableRowSorter support.
     */
    public static JTable createDirectoryTable(
            TableModel model,
            int[] minWidths,
            int[] prefWidths,
            int[] maxWidths,
            int[] alignments) {

        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                Container parent = getParent();
                if (parent instanceof JViewport) {
                    return parent.getWidth() > getPreferredSize().width;
                }
                return false;
            }

            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xFA, 0xFB, 0xF9));
                    c.setForeground(CampusTheme.TEXT_PRIMARY);
                } else {
                    c.setBackground(CampusTheme.SIDEBAR_ACTIVE);
                    c.setForeground(CampusTheme.PRIMARY);
                }
                if (c instanceof JComponent) {
                    JComponent jc = (JComponent) c;
                    jc.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                    Object val = getValueAt(row, column);
                    if (val != null && !val.toString().trim().isEmpty()) {
                        jc.setToolTipText(val.toString());
                    } else {
                        jc.setToolTipText(null);
                    }
                }
                return c;
            }
        };

        table.setFont(CampusTheme.FONT_BODY);
        table.setRowHeight(32);
        table.getTableHeader().setFont(CampusTheme.FONT_HEADING);
        table.getTableHeader().setBackground(CampusTheme.BG_SIDEBAR);
        table.getTableHeader().setForeground(CampusTheme.TEXT_PRIMARY);
        table.getTableHeader().setPreferredSize(new Dimension(table.getTableHeader().getWidth(), 36));
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setGridColor(CampusTheme.BORDER_LIGHT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // Apply column widths and alignments
        int colCount = table.getColumnCount();
        for (int i = 0; i < colCount; i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            if (minWidths != null && i < minWidths.length && minWidths[i] > 0) {
                col.setMinWidth(minWidths[i]);
            }
            if (prefWidths != null && i < prefWidths.length && prefWidths[i] > 0) {
                col.setPreferredWidth(prefWidths[i]);
            }
            if (maxWidths != null && i < maxWidths.length && maxWidths[i] > 0) {
                col.setMaxWidth(maxWidths[i]);
            }
            int align = SwingConstants.LEFT;
            if (alignments != null && i < alignments.length) {
                align = alignments[i];
            }
            final int columnAlignment = align;
            DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value,
                                                               boolean isSelected, boolean hasFocus,
                                                               int r, int c) {
                    Component comp = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, r, c);
                    setHorizontalAlignment(columnAlignment);
                    if (value != null && !value.toString().trim().isEmpty()) {
                        setToolTipText(value.toString());
                    } else {
                        setToolTipText(null);
                    }
                    if ("Status".equalsIgnoreCase(t.getColumnName(c))) {
                        String st = (value != null) ? value.toString().trim() : "";
                        if ("Active".equalsIgnoreCase(st)) {
                            comp.setForeground(CampusTheme.PRIMARY);
                            setFont(CampusTheme.FONT_BOLD);
                        } else {
                            comp.setForeground(CampusTheme.TEXT_MUTED);
                        }
                    }
                    return comp;
                }
            };
            cellRenderer.setHorizontalAlignment(align);
            col.setCellRenderer(cellRenderer);
        }

        // Install TableRowSorter
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        return table;
    }

    /**
     * Creates an institutional instant search box with a search icon, placeholder text,
     * live typing dispatch callback, and a clear button.
     */
    public static JPanel createSearchBox(String placeholder, Consumer<String> onSearch) {
        JPanel box = new JPanel(new BorderLayout(6, 0));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CampusTheme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        box.setPreferredSize(new Dimension(240, 32));
        box.setMinimumSize(new Dimension(140, 32));

        JLabel iconLabel = new JLabel(CampusIcons.get(CampusIcons.IconType.SEARCH, 14, CampusTheme.TEXT_MUTED));
        box.add(iconLabel, BorderLayout.WEST);

        JTextField field = new JTextField();
        field.setFont(CampusTheme.FONT_BODY);
        field.setBorder(null);
        field.setOpaque(false);

        JLabel placeholderLabel = new JLabel(placeholder);
        placeholderLabel.setFont(CampusTheme.FONT_SMALL);
        placeholderLabel.setForeground(CampusTheme.TEXT_MUTED);
        placeholderLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new OverlayLayout(centerPanel));
        centerPanel.setOpaque(false);
        centerPanel.add(field);
        centerPanel.add(placeholderLabel);
        box.add(centerPanel, BorderLayout.CENTER);

        JButton clearBtn = new JButton("×");
        clearBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        clearBtn.setForeground(CampusTheme.TEXT_MUTED);
        clearBtn.setBorder(null);
        clearBtn.setContentAreaFilled(false);
        clearBtn.setFocusPainted(false);
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.setVisible(false);
        clearBtn.addActionListener(e -> {
            field.setText("");
            if (onSearch != null) onSearch.accept("");
        });
        box.add(clearBtn, BorderLayout.EAST);

        field.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                String q = field.getText();
                placeholderLabel.setVisible(q.isEmpty());
                clearBtn.setVisible(!q.isEmpty());
                if (onSearch != null) {
                    onSearch.accept(q);
                }
            }
            @Override
            public void insertUpdate(DocumentEvent e) { update(); }
            @Override
            public void removeUpdate(DocumentEvent e) { update(); }
            @Override
            public void changedUpdate(DocumentEvent e) { update(); }
        });

        return box;
    }

    /**
     * Creates an institutional count badge pill displaying record counts.
     */
    public static JLabel createCountBadge(String text) {
        JLabel badge = new JLabel(text);
        badge.setFont(CampusTheme.FONT_SMALL_BOLD);
        badge.setBackground(new Color(0xE2, 0xE8, 0xDE));
        badge.setForeground(CampusTheme.PRIMARY);
        badge.setOpaque(true);
        badge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC5, 0xCD, 0xC0), 1, true),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)
        ));
        return badge;
    }
}
