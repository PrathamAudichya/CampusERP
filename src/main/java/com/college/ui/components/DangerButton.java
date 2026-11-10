package com.college.ui.components;

/**
 * High-visibility danger button for destructive operations (e.g. drop enrollment, deactivate).
 */
public class DangerButton extends StyledButton {
    public DangerButton(String text) {
        super(text, ButtonStyle.DANGER);
    }
}

