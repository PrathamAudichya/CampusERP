package com.college.ui.components;

import com.college.ui.theme.CampusTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Reusable empty state component for tables, lists, and dashboard cards.
 * Explains clearly why a section has no records and provides a direct call to action.
 */
public class EmptyStatePanel extends JPanel {

    public EmptyStatePanel(String title, String description) {
        this(CampusIcons.IconType.EMPTY_STATE, title, description, null, null);
    }

    public EmptyStatePanel(CampusIcons.IconType iconType, String title, String description) {
        this(iconType, title, description, null, null);
    }

    public EmptyStatePanel(String title, String description, String buttonText, ActionListener action) {
        this(CampusIcons.IconType.EMPTY_STATE, title, description, buttonText, action);
    }

    public EmptyStatePanel(CampusIcons.IconType iconType, String title, String description,
                           String buttonText, ActionListener action) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(new EmptyBorder(32, 24, 32, 24));

        // 1. Vector Icon
        JLabel iconLabel = new JLabel(CampusIcons.get(iconType, 42, CampusTheme.TEXT_MUTED));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(iconLabel);
        add(Box.createVerticalStrut(14));

        // 2. Title
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(CampusTheme.FONT_SUBTITLE);
        titleLabel.setForeground(CampusTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(titleLabel);
        add(Box.createVerticalStrut(6));

        // 3. Informative Description
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(CampusTheme.FONT_SMALL);
        descLabel.setForeground(CampusTheme.TEXT_SECONDARY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(descLabel);

        // 4. Optional Contextual Action Button
        if (buttonText != null && action != null) {
            add(Box.createVerticalStrut(16));
            JButton btn = new PrimaryButton(buttonText);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.addActionListener(action);
            add(btn);
        }
    }
}

