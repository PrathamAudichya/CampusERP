package com.college.ui.components;

import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Lightweight, pure Java2D vector iconography system for Campus ERP.
 *
 * <p>Replaces all Unicode emoji characters and placeholder glyphs with clean,
 * resolution-independent geometric icons styled in the Lucide design language:
 * <ul>
 *   <li>Consistent 1.5–2.0px stroke weights</li>
 *   <li>Rounded caps and joins</li>
 *   <li>Dynamically tintable (normal #68756D, active #2F6B4F, terracotta #C97855, white #FFFFFF)</li>
 *   <li>Zero external fonts or remote web assets required</li>
 * </ul>
 * </p>
 */
public final class CampusIcons {

    public enum IconType {
        DASHBOARD,
        STUDENTS,
        FACULTY,
        DEPARTMENTS,
        COURSES,
        ENROLLMENTS,
        ATTENDANCE,
        EXAMINATIONS,
        MARKS,
        FEES,
        REPORTS,
        USER_DIRECTORY,
        AUDIT_TRAIL,
        PROFILE,
        PASSWORD,
        LOGOUT,
        REFRESH,
        SEARCH,
        EMPTY_STATE,
        CHECK,
        WARNING
    }

    private CampusIcons() {
        // Prevent instantiation
    }

    /**
     * Creates an Icon instance rendered as vector graphics.
     */
    public static Icon get(IconType type, int size, Color color) {
        return new VectorIcon(type, size, color);
    }

    public static Icon get(IconType type, int size) {
        return new VectorIcon(type, size, CampusTheme.TEXT_SECONDARY);
    }

    public static Icon get(IconType type) {
        return new VectorIcon(type, 18, CampusTheme.TEXT_SECONDARY);
    }

    private static class VectorIcon implements Icon {
        private final IconType type;
        private final int size;
        private final Color color;

        VectorIcon(IconType type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);

            Color renderColor = (color != null) ? color : CampusTheme.TEXT_SECONDARY;
            g2.setColor(renderColor);

            float strokeWidth = Math.max(1.5f, size / 11.0f);
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            double s = size;

            switch (type) {
                case DASHBOARD:
                    // 4-box dashboard grid
                    double bSize = s * 0.34;
                    double gap = s * 0.12;
                    double off1 = s * 0.10;
                    double off2 = off1 + bSize + gap;
                    g2.draw(new RoundRectangle2D.Double(off1, off1, bSize, bSize, 3, 3));
                    g2.draw(new RoundRectangle2D.Double(off2, off1, bSize, bSize, 3, 3));
                    g2.draw(new RoundRectangle2D.Double(off1, off2, bSize, bSize, 3, 3));
                    g2.draw(new RoundRectangle2D.Double(off2, off2, bSize, bSize, 3, 3));
                    break;

                case STUDENTS:
                case PROFILE:
                    // User silhouette: head circle + shoulder arc
                    double headRadius = s * 0.20;
                    g2.draw(new Ellipse2D.Double(s * 0.5 - headRadius, s * 0.12, headRadius * 2, headRadius * 2));
                    Arc2D.Double shoulders = new Arc2D.Double(s * 0.16, s * 0.52, s * 0.68, s * 0.52, 0, 180, Arc2D.OPEN);
                    g2.draw(shoulders);
                    break;

                case FACULTY:
                    // User with badge
                    double fHeadRadius = s * 0.18;
                    g2.draw(new Ellipse2D.Double(s * 0.42 - fHeadRadius, s * 0.12, fHeadRadius * 2, fHeadRadius * 2));
                    g2.draw(new Arc2D.Double(s * 0.12, s * 0.52, s * 0.60, s * 0.50, 0, 180, Arc2D.OPEN));
                    // Small ID badge on right
                    g2.draw(new RoundRectangle2D.Double(s * 0.64, s * 0.40, s * 0.26, s * 0.36, 2, 2));
                    break;

                case DEPARTMENTS:
                    // Institutional building with 3 columns and pediment
                    Path2D.Double roof = new Path2D.Double();
                    roof.moveTo(s * 0.15, s * 0.34);
                    roof.lineTo(s * 0.50, s * 0.12);
                    roof.lineTo(s * 0.85, s * 0.34);
                    roof.closePath();
                    g2.draw(roof);
                    // Base line
                    g2.draw(new Line2D.Double(s * 0.12, s * 0.86, s * 0.88, s * 0.86));
                    // Columns
                    g2.draw(new Line2D.Double(s * 0.26, s * 0.38, s * 0.26, s * 0.82));
                    g2.draw(new Line2D.Double(s * 0.50, s * 0.38, s * 0.50, s * 0.82));
                    g2.draw(new Line2D.Double(s * 0.74, s * 0.38, s * 0.74, s * 0.82));
                    break;

                case COURSES:
                    // Open book / curriculum
                    Path2D.Double book = new Path2D.Double();
                    book.moveTo(s * 0.16, s * 0.24);
                    book.lineTo(s * 0.48, s * 0.30);
                    book.lineTo(s * 0.48, s * 0.84);
                    book.lineTo(s * 0.16, s * 0.78);
                    book.closePath();
                    book.moveTo(s * 0.84, s * 0.24);
                    book.lineTo(s * 0.52, s * 0.30);
                    book.lineTo(s * 0.52, s * 0.84);
                    book.lineTo(s * 0.84, s * 0.78);
                    book.closePath();
                    g2.draw(book);
                    break;

                case ENROLLMENTS:
                    // Checklist / user addition
                    g2.draw(new RoundRectangle2D.Double(s * 0.18, s * 0.14, s * 0.64, s * 0.72, 4, 4));
                    Path2D.Double checkMark = new Path2D.Double();
                    checkMark.moveTo(s * 0.32, s * 0.48);
                    checkMark.lineTo(s * 0.44, s * 0.60);
                    checkMark.lineTo(s * 0.68, s * 0.36);
                    g2.draw(checkMark);
                    break;

                case ATTENDANCE:
                    // Calendar with checkmark
                    g2.draw(new RoundRectangle2D.Double(s * 0.16, s * 0.20, s * 0.68, s * 0.66, 3, 3));
                    g2.draw(new Line2D.Double(s * 0.16, s * 0.40, s * 0.84, s * 0.40));
                    g2.draw(new Line2D.Double(s * 0.34, s * 0.12, s * 0.34, s * 0.24));
                    g2.draw(new Line2D.Double(s * 0.66, s * 0.12, s * 0.66, s * 0.24));
                    // Dot
                    g2.fill(new Ellipse2D.Double(s * 0.45, s * 0.56, s * 0.10, s * 0.10));
                    break;

                case EXAMINATIONS:
                    // Clipboard with check
                    g2.draw(new RoundRectangle2D.Double(s * 0.20, s * 0.20, s * 0.60, s * 0.68, 3, 3));
                    g2.draw(new RoundRectangle2D.Double(s * 0.38, s * 0.12, s * 0.24, s * 0.14, 2, 2));
                    g2.draw(new Line2D.Double(s * 0.32, s * 0.44, s * 0.68, s * 0.44));
                    g2.draw(new Line2D.Double(s * 0.32, s * 0.60, s * 0.56, s * 0.60));
                    break;

                case MARKS:
                    // Academic award medal
                    double medalRadius = s * 0.24;
                    g2.draw(new Ellipse2D.Double(s * 0.5 - medalRadius, s * 0.14, medalRadius * 2, medalRadius * 2));
                    Path2D.Double ribbons = new Path2D.Double();
                    ribbons.moveTo(s * 0.38, s * 0.56);
                    ribbons.lineTo(s * 0.32, s * 0.88);
                    ribbons.lineTo(s * 0.50, s * 0.78);
                    ribbons.lineTo(s * 0.68, s * 0.88);
                    ribbons.lineTo(s * 0.62, s * 0.56);
                    g2.draw(ribbons);
                    break;

                case FEES:
                    // Rupee symbol or clean wallet/coin card
                    g2.draw(new RoundRectangle2D.Double(s * 0.14, s * 0.24, s * 0.72, s * 0.54, 4, 4));
                    g2.draw(new Line2D.Double(s * 0.14, s * 0.40, s * 0.86, s * 0.40));
                    g2.draw(new Ellipse2D.Double(s * 0.62, s * 0.54, s * 0.12, s * 0.12));
                    break;

                case REPORTS:
                    // Analytical bar chart
                    g2.draw(new Line2D.Double(s * 0.14, s * 0.84, s * 0.86, s * 0.84));
                    g2.draw(new Line2D.Double(s * 0.14, s * 0.16, s * 0.14, s * 0.84));
                    g2.draw(new RoundRectangle2D.Double(s * 0.24, s * 0.52, s * 0.14, s * 0.32, 2, 2));
                    g2.draw(new RoundRectangle2D.Double(s * 0.44, s * 0.34, s * 0.14, s * 0.50, 2, 2));
                    g2.draw(new RoundRectangle2D.Double(s * 0.64, s * 0.22, s * 0.14, s * 0.62, 2, 2));
                    break;

                case USER_DIRECTORY:
                    // Security shield
                    Path2D.Double shield = new Path2D.Double();
                    shield.moveTo(s * 0.50, s * 0.12);
                    shield.lineTo(s * 0.82, s * 0.24);
                    shield.lineTo(s * 0.82, s * 0.52);
                    shield.curveTo(s * 0.82, s * 0.74, s * 0.50, s * 0.88, s * 0.50, s * 0.88);
                    shield.curveTo(s * 0.50, s * 0.88, s * 0.18, s * 0.74, s * 0.18, s * 0.52);
                    shield.lineTo(s * 0.18, s * 0.24);
                    shield.closePath();
                    g2.draw(shield);
                    break;

                case AUDIT_TRAIL:
                    // Clock with circular arrow
                    g2.draw(new Ellipse2D.Double(s * 0.18, s * 0.18, s * 0.64, s * 0.64));
                    g2.draw(new Line2D.Double(s * 0.50, s * 0.32, s * 0.50, s * 0.50));
                    g2.draw(new Line2D.Double(s * 0.50, s * 0.50, s * 0.66, s * 0.50));
                    break;

                case PASSWORD:
                    // Padlock
                    g2.draw(new RoundRectangle2D.Double(s * 0.24, s * 0.42, s * 0.52, s * 0.44, 4, 4));
                    g2.draw(new Arc2D.Double(s * 0.34, s * 0.18, s * 0.32, s * 0.40, 0, 180, Arc2D.OPEN));
                    g2.fill(new Ellipse2D.Double(s * 0.46, s * 0.58, s * 0.08, s * 0.08));
                    break;

                case LOGOUT:
                    // Doorway with exit arrow
                    g2.draw(new Line2D.Double(s * 0.55, s * 0.18, s * 0.20, s * 0.18));
                    g2.draw(new Line2D.Double(s * 0.20, s * 0.18, s * 0.20, s * 0.82));
                    g2.draw(new Line2D.Double(s * 0.20, s * 0.82, s * 0.55, s * 0.82));
                    // Arrow pointing right
                    g2.draw(new Line2D.Double(s * 0.40, s * 0.50, s * 0.82, s * 0.50));
                    Path2D.Double arrow = new Path2D.Double();
                    arrow.moveTo(s * 0.68, s * 0.36);
                    arrow.lineTo(s * 0.84, s * 0.50);
                    arrow.lineTo(s * 0.68, s * 0.64);
                    g2.draw(arrow);
                    break;

                case REFRESH:
                    // Rotating refresh circle
                    g2.draw(new Arc2D.Double(s * 0.18, s * 0.18, s * 0.64, s * 0.64, 45, 270, Arc2D.OPEN));
                    Path2D.Double refArrow = new Path2D.Double();
                    refArrow.moveTo(s * 0.60, s * 0.16);
                    refArrow.lineTo(s * 0.80, s * 0.24);
                    refArrow.lineTo(s * 0.72, s * 0.44);
                    g2.draw(refArrow);
                    break;

                case SEARCH:
                    // Magnifying glass
                    double magR = s * 0.26;
                    g2.draw(new Ellipse2D.Double(s * 0.20, s * 0.20, magR * 2, magR * 2));
                    g2.draw(new Line2D.Double(s * 0.58, s * 0.58, s * 0.82, s * 0.82));
                    break;

                case CHECK:
                    Path2D.Double chk = new Path2D.Double();
                    chk.moveTo(s * 0.20, s * 0.50);
                    chk.lineTo(s * 0.42, s * 0.72);
                    chk.lineTo(s * 0.82, s * 0.28);
                    g2.draw(chk);
                    break;

                case WARNING:
                    Path2D.Double tri = new Path2D.Double();
                    tri.moveTo(s * 0.50, s * 0.14);
                    tri.lineTo(s * 0.88, s * 0.82);
                    tri.lineTo(s * 0.12, s * 0.82);
                    tri.closePath();
                    g2.draw(tri);
                    g2.draw(new Line2D.Double(s * 0.50, s * 0.38, s * 0.50, s * 0.58));
                    g2.fill(new Ellipse2D.Double(s * 0.46, s * 0.68, s * 0.08, s * 0.08));
                    break;

                case EMPTY_STATE:
                    // Open folder / empty inbox
                    g2.draw(new RoundRectangle2D.Double(s * 0.14, s * 0.28, s * 0.72, s * 0.54, 4, 4));
                    g2.draw(new Line2D.Double(s * 0.14, s * 0.52, s * 0.36, s * 0.52));
                    g2.draw(new Line2D.Double(s * 0.36, s * 0.52, s * 0.44, s * 0.62));
                    g2.draw(new Line2D.Double(s * 0.44, s * 0.62, s * 0.56, s * 0.62));
                    g2.draw(new Line2D.Double(s * 0.56, s * 0.62, s * 0.64, s * 0.52));
                    g2.draw(new Line2D.Double(s * 0.64, s * 0.52, s * 0.86, s * 0.52));
                    break;
            }

            g2.dispose();
        }
    }
}

