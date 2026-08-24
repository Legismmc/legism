package net.legacylauncher.ui.theme;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.ui.FlatLaf;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.U;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class SystemTheme extends Theme {
    static final int MAX_ARC = 64, MAX_BORDER = 24, BLACK_MIN = 64;

    private static final SystemTheme instance = new SystemTheme();

    public static SystemTheme getSystemTheme() {
        return instance;
    }

    private final JLabel component;
    private final Map<Border, Color> borderColorMap;

    private final Color
            success = new Color(78, 196, 78),
            failure = new Color(179, 0, 0);
    private Color shadow, semiForeground, panelBackground;

    private SystemTheme() {
        super("system");
        this.component = new JLabel();

        this.borderColorMap = new HashMap<>();
        borderColorMap.put(Border.MAIN_PANEL, new Color(28, 128, 28, 255));
        borderColorMap.put(Border.ADDITIONAL_PANEL, new Color(255, 177, 177));
        borderColorMap.put(Border.SETTINGS_PANEL, new Color(217, 217, 217, 255));

        assert borderColorMap.size() == Border.values().length;

        updateDerivingColors();
    }

    private void updateDerivingColors() {
        this.semiForeground = U.shiftColor(getForeground(), 96, 64, 192);
        this.panelBackground = U.shiftAlpha(getBackground(), -176, 64, 192);
        this.shadow = U.shiftAlpha(useDarkTheme() ? U.shiftColor(getForeground(), -96) : getBackground(), -150);
    }

    @Override
    public Color getForeground() {
        return component.getForeground();
    }

    @Override
    public Color getSemiForeground() {
        return semiForeground;
    }

    @Override
    public Color getBackground() {
        return component.getBackground();
    }

    @Override
    public Color getPanelBackground() {
        return panelBackground;
    }

    @Override
    public Color getSuccess() {
        return success;
    }

    @Override
    public Color getFailure() {
        return failure;
    }

    @Override
    public int getBorderSize() {
        return isMinimalist() ? 1 : 2;
    }

    @Override
    public Color getBorder(Border border) {
        return borderColorMap.get(Objects.requireNonNull(border, "border"));
    }

    @Override
    public Color getShadow(Border border) {
        return shadow;
    }

    @Override
    public int getArc(Border border) {
        if (isMinimalist()) {
            return 0;
        }
        return border == Border.SETTINGS_PANEL ? 16 : 24;
    }

    @Override
    public Color getIconColor(String iconName) {
        return useColorfulIcons() ? ColorfulIcons.getColor(iconName) : getForeground();
    }

    @Override
    public boolean useDarkTheme() {
        return !useColorfulIcons();
    }

    private boolean useColorfulIcons() {
        if (isMinimalist() || Boolean.getBoolean("tlauncher.ui.noColorfulIcons") || UIManager.getBoolean("laf.dark")) {
            return false;
        }
        Color background = getBackground();
        return background.getRed() > BLACK_MIN || background.getGreen() > BLACK_MIN || background.getBlue() > BLACK_MIN;
    }

    /**
     * Whether the "Minimalist" theme is the one selected in Settings; checked directly
     * against the settings map rather than cached, since a theme change swaps it at
     * runtime and every one of these getters is called fresh on each repaint anyway.
     */
    private static boolean isMinimalist() {
        try {
            return LegacyLauncher.getInstance().getSettings().getFlatLafConfiguration()
                    .map(FlatLaf::isMinimalist)
                    .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void updateUI() {
        component.updateUI();
        updateDerivingColors();
    }
}
