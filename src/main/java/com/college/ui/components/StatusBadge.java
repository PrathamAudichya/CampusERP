package com.college.ui.components;

import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Accessible status and role pill badge with guaranteed high-contrast dark text
 * on soft pastel backgrounds.
 */
public class StatusBadge extends JComponent {

    public enum BadgeType {
        ACTIVE(new Color(0xDC, 0xFC, 0xE7), new Color(0x16, 0x65, 0x34), new Color(0x86, 0xEF, 0xAC)),
        PENDING(new Color(0xFE, 0xF3, 0xC7), new Color(0x92, 0x40, 0x0E), new Color(0xFC, 0xD3, 0x4D)),
        DROPPED(new Color(0xFE, 0xE2, 0xE2), new Color(0x99, 0x1B, 0x1B), new Color(0xFC, 0xA5, 0xA5)),
        COMPLETED(new Color(0xDB, 0xEA, 0xFE), new Color(0x1E, 0x40, 0xAF), new Color(0x93, 0xC5, 0xFD)),
        ADMIN(new Color(0xE0, 0xEC, 0xE3), new Color(0x2F, 0x6B, 0x4F), new Color(0xA3, 0xC9, 0xB2)),
        FACULTY(new Color(0xDE, 0xEE, 0xF5), new Color(0x2E, 0x6F, 0x8A), new Color(0x98, 0xC5, 0xD9)),
        STUDENT(new Color(0xFA, 0xEE, 0xE8), new Color(0xC9, 0x78, 0x55), new Color(0xE8, 0xBB, 0xA8)),
        NEUTRAL(new Color(0xF1, 0xF5, 0xF9), new Color(0x33, 0x41, 0x55), new Color(0xCB, 0xD5, 0xE1));

        final Color bg;
        final Color fg;
        final Color border;

        BadgeType(Color bg, Color fg, Color border) {
            this.bg = bg;
            this.fg = fg;
            this.border = border;
        }
    }

    private final String text;
    private final BadgeType type;

    public StatusBadge(String text, BadgeType type) {
        this.text = text;
        this.type = type;
        setFont(CampusTheme.FONT_SMALL_BOLD);
        setOpaque(false);
    }

    public static StatusBadge of(String status) {
        if (status == null) return new StatusBadge("UNKNOWN", BadgeType.NEUTRAL);
        String s = status.toUpperCase().trim();
        switch (s) {
            case "ACTIVE":
            case "APPROVED":
            case "PRESENT":
            case "SUCCESS":
                return new StatusBadge(s, BadgeType.ACTIVE);
            case "PENDING":
            case "LATE":
            case "WARNING":
                return new StatusBadge(s, BadgeType.PENDING);
            case "DROPPED":
            case "INACTIVE":
            case "REJECTED":
            case "ABSENT":
            case "FAILED":
                return new StatusBadge(s, BadgeType.DROPPED);
            case "COMPLETED":
            case "PASS":
            case "ENROLLED":
                return new StatusBadge(s, BadgeType.COMPLETED);
            case "ADMIN":
                return new StatusBadge("ADMINISTRATOR", BadgeType.ADMIN);
            case "FACULTY":
                return new StatusBadge("FACULTY", BadgeType.FACULTY);
            case "STUDENT":
                return new StatusBadge("STUDENT", BadgeType.STUDENT);
            case "YES":
                return new StatusBadge("YES", BadgeType.PENDING);
            case "NO":
                return new StatusBadge("NO", BadgeType.NEUTRAL);
            default:
                return new StatusBadge(s, BadgeType.NEUTRAL);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(text) + 20;
        int h = fm.getHeight() + 8;
        return new Dimension(Math.max(w, 56), Math.max(h, 24));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int w = getWidth();
        int h = getHeight();

        // Background pill
        g2.setColor(type.bg);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h, h));

        // Border
        g2.setColor(type.border);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h, h));

        // Text
        g2.setFont(getFont());
        g2.setColor(type.fg);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, tx, ty);

        g2.dispose();
    }
}

