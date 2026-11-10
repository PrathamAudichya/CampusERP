package com.college.ui.theme;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

/**
 * Central design system, institutional color palette, typography hierarchy,
 * and global Swing UIManager configuration for Campus ERP v2.0.5.
 *
 * <p>Enforces a warm, light, architectural aesthetic:
 * <ul>
 *   <li>Forest Green (#2F6B4F) primary interactive accent</li>
 *   <li>Terracotta (#C97855) secondary warm highlights</li>
 *   <li>Warm off-white canvas (#F5F6F2) and clean white cards (#FFFFFF)</li>
 *   <li>Light sage/neutral sidebar (#ECEFE8) and active pills (#E0ECE3)</li>
 *   <li>High-contrast deep forest typography (#26332B)</li>
 * </ul>
 * </p>
 */
public final class CampusTheme {

    // --- New v2.0.5 Warm Institutional Color Palette ---
    public static final Color CANVAS = new Color(0xF5, 0xF6, 0xF2);         // #F5F6F2 (Application canvas)
    public static final Color CARD = new Color(0xFF, 0xFF, 0xFF);           // #FFFFFF (Clean card surface)
    public static final Color SIDEBAR = new Color(0xEC, 0xEF, 0xE8);        // #ECEFE8 (Light sage sidebar)
    public static final Color SIDEBAR_HOVER = new Color(0xE3, 0xE7, 0xDE);  // #E3E7DE (Sidebar hover)
    public static final Color SIDEBAR_ACTIVE = new Color(0xE0, 0xEC, 0xE3); // #E0ECE3 (Active navigation pill)

    // Primary Text & Muted Typography
    public static final Color TEXT_PRIMARY = new Color(0x26, 0x33, 0x2B);   // #26332B (Deep forest text)
    public static final Color TEXT_SECONDARY = new Color(0x68, 0x75, 0x6D); // #68756D (Medium sage text)
    public static final Color TEXT_MUTED = new Color(0x8A, 0x96, 0x8F);     // #8A968F (Muted labels/metadata)

    // Primary Interactive Accent (Forest Green)
    public static final Color PRIMARY = new Color(0x2F, 0x6B, 0x4F);        // #2F6B4F (Forest green)
    public static final Color PRIMARY_HOVER = new Color(0x24, 0x56, 0x3F);  // #24563F (Deep forest hover)
    public static final Color PRIMARY_PRESSED = new Color(0x1B, 0x43, 0x31);// #1B4331 (Forest pressed)

    // Secondary Warm Accent (Terracotta)
    public static final Color TERRACOTTA = new Color(0xC9, 0x78, 0x55);     // #C97855 (Warm terracotta)
    public static final Color TERRACOTTA_HOVER = new Color(0xB5, 0x69, 0x47);
    public static final Color TERRACOTTA_LIGHT = new Color(0xFA, 0xEE, 0xE8);

    // Borders & Dividers
    public static final Color BORDER = new Color(0xDC, 0xE3, 0xDA);         // #DCE3DA (Soft sage gray border)
    public static final Color BORDER_LIGHT = new Color(0xEA, 0xEF, 0xE8);   // #EAEFE8 (Subtle row dividers)

    // Feedback & Status Indicators
    public static final Color SUCCESS = new Color(0x2D, 0x7A, 0x53);        // #2D7A53 (Muted emerald)
    public static final Color SUCCESS_BG = new Color(0xDE, 0xF0, 0xE5);     // #DEF0E5
    public static final Color WARNING = new Color(0xA6, 0x6C, 0x1D);        // #A66C1D (Warm amber)
    public static final Color WARNING_BG = new Color(0xFC, 0xF1, 0xDF);     // #FCF1DF
    public static final Color DANGER = new Color(0xB6, 0x4A, 0x4A);         // #B64A4A (Soft crimson)
    public static final Color DANGER_HOVER = new Color(0x9E, 0x3D, 0x3D);
    public static final Color DANGER_BG = new Color(0xF9, 0xDF, 0xDF);       // #F9DFDF
    public static final Color INFO = new Color(0x2E, 0x6F, 0x8A);           // #2E6F8A (Slate teal)
    public static final Color INFO_BG = new Color(0xDE, 0xEE, 0xF5);        // #DEEEF5

    // Aliases for backwards compatibility
    public static final Color BG_MAIN = CANVAS;
    public static final Color BG_CARD = CARD;
    public static final Color BG_SIDEBAR = SIDEBAR;
    public static final Color NAVY = TEXT_PRIMARY;
    public static final Color INDIGO = PRIMARY;
    public static final Color INDIGO_HOVER = PRIMARY_HOVER;
    public static final Color INDIGO_PRESSED = PRIMARY_PRESSED;
    public static final Color TEAL = PRIMARY;
    public static final Color TEAL_LIGHT = SIDEBAR_ACTIVE;
    public static final Color AMBER = WARNING;
    public static final Color AMBER_LIGHT = WARNING_BG;
    public static final Color ERROR = DANGER;
    public static final Color ERROR_BG = DANGER_BG;

    // --- Typography Hierarchy (Segoe UI with standard platform fallbacks) ---
    private static final String PREFERRED_FONT_FAMILY = "Segoe UI";

    public static final Font FONT_HERO = resolveFont(Font.BOLD, 22);
    public static final Font FONT_TITLE = resolveFont(Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = resolveFont(Font.BOLD, 14);
    public static final Font FONT_HEADING = resolveFont(Font.BOLD, 13);
    public static final Font FONT_BODY = resolveFont(Font.PLAIN, 12);
    public static final Font FONT_BOLD = resolveFont(Font.BOLD, 12);
    public static final Font FONT_SMALL = resolveFont(Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = resolveFont(Font.BOLD, 11);
    public static final Font FONT_MICRO = resolveFont(Font.PLAIN, 10);

    private CampusTheme() {
        // Prevent instantiation
    }

    private static Font resolveFont(int style, int size) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontNames = ge.getAvailableFontFamilyNames();
        for (String fn : fontNames) {
            if (PREFERRED_FONT_FAMILY.equalsIgnoreCase(fn)) {
                return new Font(PREFERRED_FONT_FAMILY, style, size);
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    /**
     * Installs global Swing UIManager settings, fonts, table defaults,
     * FlatLaf Light Look & Feel, and high-contrast color defaults.
     */
    public static void installGlobalTheme() {
        try {
            // Initialize FlatLaf Light modern look and feel
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Throwable t) {
            try {
                // Graceful fallback to system look and feel if FlatLaf cannot initialize
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Keep default L&F
            }
        }

        // FlatLaf modern component geometry
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 6);
        UIManager.put("ProgressBar.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.showButtons", true);
        UIManager.put("ScrollBar.width", 12);

        // Apply clean fonts globally across standard Swing components
        FontUIResource bodyFont = new FontUIResource(FONT_BODY);
        FontUIResource boldFont = new FontUIResource(FONT_BOLD);

        UIManager.put("Label.font", bodyFont);
        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("Button.font", boldFont);
        UIManager.put("TextField.font", bodyFont);
        UIManager.put("PasswordField.font", bodyFont);
        UIManager.put("TextArea.font", bodyFont);
        UIManager.put("ComboBox.font", bodyFont);
        UIManager.put("CheckBox.font", bodyFont);
        UIManager.put("RadioButton.font", bodyFont);
        UIManager.put("List.font", bodyFont);
        UIManager.put("Table.font", bodyFont);
        UIManager.put("TableHeader.font", new FontUIResource(FONT_HEADING));
        UIManager.put("TitledBorder.font", new FontUIResource(FONT_SUBTITLE));
        UIManager.put("TabbedPane.font", boldFont);
        UIManager.put("ToolTip.font", new FontUIResource(FONT_SMALL));

        // Global Table and Selection styling
        UIManager.put("Table.background", CARD);
        UIManager.put("Table.foreground", TEXT_PRIMARY);
        UIManager.put("Table.selectionBackground", SIDEBAR_ACTIVE);
        UIManager.put("Table.selectionForeground", TEXT_PRIMARY);
        UIManager.put("Table.gridColor", BORDER_LIGHT);
        UIManager.put("TableHeader.background", SIDEBAR);
        UIManager.put("TableHeader.foreground", TEXT_PRIMARY);
        UIManager.put("TableHeader.height", 36);
        UIManager.put("Table.rowHeight", 32);

        // Global Panel and Dialog defaults
        UIManager.put("Panel.background", CANVAS);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("ScrollPane.background", CANVAS);
    }
}
