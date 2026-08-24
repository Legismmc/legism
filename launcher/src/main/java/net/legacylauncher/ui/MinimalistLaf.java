package net.legacylauncher.ui;

import javax.swing.UIManager;

/**
 * UI defaults layered on top of FlatLaf's light theme for the "Minimalist" option: square
 * corners, a thinner focus ring and a slimmer scrollbar instead of FlatLight's rounded,
 * shadowed look.
 * <p>
 * These are plain {@link UIManager} overrides, applied right after the look and feel is
 * installed - the way FlatLaf itself documents customizing UI defaults at runtime - rather
 * than a whole second look and feel class.
 */
final class MinimalistLaf {

    private MinimalistLaf() {
    }

    static void apply() {
        UIManager.put("Button.arc", 0);
        UIManager.put("Component.arc", 0);
        UIManager.put("ProgressBar.arc", 0);
        UIManager.put("TextComponent.arc", 0);
        UIManager.put("CheckBox.arc", 0);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.borderWidth", 1);
        UIManager.put("Button.default.boldText", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 0);
        UIManager.put("ScrollBar.trackArc", 0);
        UIManager.put("ScrollBar.showButtons", true);
        UIManager.put("TabbedPane.tabArc", 0);
        UIManager.put("TabbedPane.cardArc", 0);
    }
}
