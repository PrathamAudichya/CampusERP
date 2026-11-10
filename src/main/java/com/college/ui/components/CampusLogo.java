package com.college.ui.components;

import com.college.ui.theme.CampusTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Modern, minimalist geometric monogram logo component for Campus ERP v2.0.5.
 *
 * <p>Replaces the traditional heraldic shield with an original geometric 'C' monogram:
 * <ul>
 *   <li>Forest Green (#2F6B4F) sweeping geometric arc representing community and institutional continuity</li>
 *   <li>Warm Terracotta (#C97855) circular focal node representing scholarship, learning, and progress</li>
 *   <li>Scalable vector rendering with full antialiasing at 24px, 32px, 48px, 96px, and 256px</li>
 *   <li>Supports both compact icon-only and horizontal wordmark variants</li>
 * </ul>
 * </p>
 */
public class CampusLogo extends JComponent {

    public enum Variant {
        ICON_ONLY,
        HORIZONTAL
    }

    public enum LogoVariant {
        ICON_ONLY,
        HORIZONTAL
    }

    private final int markSize;
    private final Variant variant;

    public CampusLogo(int size) {
        this(size, Variant.ICON_ONLY);
    }

    public CampusLogo(int markSize, Variant variant) {
        this.markSize = markSize;
        this.variant = variant;
        setOpaque(false);
        int totalWidth = (variant == Variant.HORIZONTAL) ? markSize + 170 : markSize;
        int totalHeight = Math.max(markSize, (variant == Variant.HORIZONTAL ? 42 : markSize));
        setPreferredSize(new Dimension(totalWidth, totalHeight));
    }

    public CampusLogo(int markSize, LogoVariant logoVariant) {
        this(markSize, logoVariant == LogoVariant.HORIZONTAL ? Variant.HORIZONTAL : Variant.ICON_ONLY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int iconY = (getHeight() - markSize) / 2;
        paintGeometricMonogram(g2, 0, iconY, markSize);

        if (variant == Variant.HORIZONTAL) {
            paintWordmark(g2, markSize + 12, 0, getWidth() - markSize - 12, getHeight());
        }

        g2.dispose();
    }

    /**
     * Renders the geometric 'C' monogram with forest green arc and terracotta progress node.
     */
    public static void paintGeometricMonogram(Graphics2D g2, int x, int y, int size) {
        double cx = x + size / 2.0;
        double cy = y + size / 2.0;

        // Dynamic proportions based on size
        double pad = size * 0.10;
        double diameter = size - (pad * 2);
        double strokeWidth = Math.max(2.5, size * 0.17);

        // 1. Forest Green 'C' Arc (Starts at 45 deg, sweeps 270 deg counter-clockwise)
        double arcX = cx - diameter / 2.0;
        double arcY = cy - diameter / 2.0;
        Arc2D.Double arc = new Arc2D.Double(arcX, arcY, diameter, diameter, 45, 270, Arc2D.OPEN);

        g2.setColor(CampusTheme.PRIMARY);
        g2.setStroke(new BasicStroke((float) strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(arc);

        // 2. Terracotta Progress/Learning Node in the upper aperture
        double nodeRadius = strokeWidth * 0.58;
        // Calculate angle near 42 degrees on the arc radius
        double angleRad = Math.toRadians(42);
        double nodeDist = diameter / 2.0;
        double nodeX = cx + (nodeDist * Math.cos(angleRad));
        double nodeY = cy - (nodeDist * Math.sin(angleRad));

        Ellipse2D.Double node = new Ellipse2D.Double(
                nodeX - nodeRadius,
                nodeY - nodeRadius,
                nodeRadius * 2,
                nodeRadius * 2
        );
        g2.setColor(CampusTheme.TERRACOTTA);
        g2.fill(node);

        // 3. Subtle inner geometric accent notch at the lower aperture (for distinctiveness)
        if (size >= 32) {
            double lowerAngleRad = Math.toRadians(-42);
            double lowerNodeRadius = strokeWidth * 0.38;
            double lowerX = cx + (nodeDist * Math.cos(lowerAngleRad));
            double lowerY = cy - (nodeDist * Math.sin(lowerAngleRad));

            Ellipse2D.Double lowerDot = new Ellipse2D.Double(
                    lowerX - lowerNodeRadius,
                    lowerY - lowerNodeRadius,
                    lowerNodeRadius * 2,
                    lowerNodeRadius * 2
            );
            g2.setColor(CampusTheme.PRIMARY_HOVER);
            g2.fill(lowerDot);
        }
    }

    private void paintWordmark(Graphics2D g2, int x, int y, int width, int height) {
        int titleFontSize = Math.max(15, (int) (markSize * 0.44));
        int subFontSize = Math.max(10, (int) (markSize * 0.28));

        Font titleFont = new Font("Segoe UI", Font.BOLD, titleFontSize);
        Font subFont = new Font("Segoe UI", Font.PLAIN, subFontSize);

        g2.setFont(titleFont);
        g2.setColor(CampusTheme.TEXT_PRIMARY);
        FontMetrics fmTitle = g2.getFontMetrics();
        int titleY = y + (height / 2) - 1;
        g2.drawString("CAMPUS ERP", x, titleY);

        g2.setFont(subFont);
        g2.setColor(CampusTheme.TEXT_SECONDARY);
        int subY = titleY + fmTitle.getDescent() + 10;
        g2.drawString("University Platform", x, subY);
    }

    /**
     * Creates a high-resolution BufferedImage of the compact monogram logo.
     */
    public static Image createLogoImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        paintGeometricMonogram(g2, 0, 0, Math.min(width, height));
        g2.dispose();
        return img;
    }

    /**
     * Generates a 256x256 anti-aliased PNG logo and saves to disk.
     */
    public static void saveResourcePng(int size, File dest) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        paintGeometricMonogram(g2, 0, 0, size);
        g2.dispose();
        if (dest.getParentFile() != null) {
            dest.getParentFile().mkdirs();
        }
        ImageIO.write(img, "png", dest);
    }
}
