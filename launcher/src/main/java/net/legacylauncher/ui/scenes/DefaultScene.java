package net.legacylauncher.ui.scenes;

import net.legacylauncher.minecraft.auth.Account;
import net.legacylauncher.ui.MainPane;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.notification.NotificationPanel;
import net.legacylauncher.ui.settings.SettingsPanel;
import net.legacylauncher.ui.swing.DelayedComponent;
import net.legacylauncher.ui.swing.DelayedComponentLoader;
import net.legacylauncher.ui.swing.extended.ExtendedPanel;
import net.legacylauncher.util.SwingUtil;

import java.awt.*;

public class DefaultScene extends PseudoScene {
    public static final Dimension LOGIN_SIZE = new Dimension(285, 240);
    public static final Dimension SETTINGS_SIZE = new Dimension(600, 550);

    public final LoginForm loginForm;
    public final DelayedComponent<SettingsPanel> settingsForm;
    private DefaultScene.SidePanel sidePanel;
    private ExtendedPanel sidePanelComp;
    public final NotificationPanel notificationPanel;

    public DefaultScene(MainPane main) {
        super(main);
        settingsForm = new DelayedComponent<>(new DelayedComponentLoader<SettingsPanel>() {
            @Override
            public SettingsPanel loadComponent() {
                return new SettingsPanel(DefaultScene.this);
            }

            @Override
            public void onComponentLoaded(SettingsPanel loaded) {
                loaded.setVisible(false);
                DefaultScene.this.add(loaded);
                loaded.setSize(SwingUtil.magnify(SETTINGS_SIZE));
                loaded.ready = true;
            }
        });
        //settingsForm.setVisible(false);
        //add(settingsForm);
        // The instance screen is the launcher's home now, so the login form is no longer
        // shown - but it is still built: it drives authentication and the launch itself,
        // and the rest of the launcher reaches through it for the selected version.
        loginForm = new LoginForm(this);
        loginForm.setSize(SwingUtil.magnify(LOGIN_SIZE));
        loginForm.setVisible(false);
        this.notificationPanel = new NotificationPanel();
        add(notificationPanel);

    }

    public void setShown(boolean shown, boolean animate) {
        super.setShown(shown, animate);
        if (shown) {
            if (getMainPane().accountManager.isLoaded()) {
                Account<?> selected = getMainPane().accountManager.get().list.getSelected();
                if (selected != null) {
                    loginForm.accounts.setAccount(selected);
                }
            }
        }
    }

    public void onResize() {
        if (parent != null) {
            setBounds(0, 0, parent.getWidth(), parent.getHeight());
            updateCoords();
        }
    }

    private static final int MARGIN = 10, SPACE_BETWEEN = 15;

    /**
     * With the login form gone from the screen, the only thing left to place is the
     * settings panel, which simply takes the middle.
     */
    private void updateCoords() {
        if (sidePanelComp != null) {
            sidePanelComp.setLocation(
                    getWidth() / 2 - sidePanelComp.getWidth() / 2,
                    getHeight() / 2 - sidePanelComp.getHeight() / 2);
        }
        notificationPanel.setBounds(0, 0, getWidth(), notificationPanel.height);
    }

    public DefaultScene.SidePanel getSidePanel() {
        return sidePanel;
    }

    public void setSidePanel(DefaultScene.SidePanel side) {
        if (sidePanel != side) {
            boolean noSidePanel = side == null;
            if (sidePanelComp != null) {
                sidePanelComp.setVisible(false);
            }

            sidePanel = side;
            sidePanelComp = noSidePanel ? null : getSidePanelComp(side);
            if (!noSidePanel) {
                sidePanelComp.setVisible(true);
            }

            updateCoords();

            validate();
            repaint();

            if (sidePanelComp != null) {
                sidePanelComp.validate();
                sidePanelComp.repaint();
            } else {
                // nothing is left on this scene once the settings close, so hand the
                // screen back to the instance list the user came from
                getMainPane().openInstancesScene();
            }
        }
    }

    public void toggleSidePanel(DefaultScene.SidePanel side) {
        if (sidePanel == side) {
            side = null;
        }

        setSidePanel(side);
    }

    public ExtendedPanel getSidePanelComp(DefaultScene.SidePanel side) {
        if (side == null) {
            throw new NullPointerException("side");
        } else {
            switch (side) {
                case SETTINGS:
                    return settingsForm.get();
                default:
                    throw new RuntimeException("unknown side:" + side);
            }
        }
    }

    public enum SidePanel {
        SETTINGS;

        public final boolean requiresShow;

        SidePanel(boolean requiresShow) {
            this.requiresShow = requiresShow;
        }

        SidePanel() {
            this(false);
        }
    }
}
