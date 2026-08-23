package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.ui.LegacyLauncherFrame;
import net.legacylauncher.ui.block.Blockable;
import net.legacylauncher.ui.block.Blocker;
import net.legacylauncher.ui.loc.LocalizableButton;
import net.legacylauncher.ui.loc.LocalizableMenuItem;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.swing.extended.BorderPanel;
import net.minecraft.launcher.updater.VersionSyncInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class PlayButton extends BorderPanel implements Blockable, LoginForm.LoginStateListener {
    private static final long serialVersionUID = 6944074583143406549L;
    private PlayButton.PlayButtonState state;
    private final LoginForm loginForm;

    private final LocalizableButton button;

    private int mouseX, mouseY;
    private final JPopupMenu wrongButtonMenu = new JPopupMenu();

    {
        LocalizableMenuItem wrongButtonItem = new LocalizableMenuItem("loginform.wrongbutton");
        wrongButtonItem.setEnabled(false);
        wrongButtonItem.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                wrongButtonMenu.setVisible(false);
            }
        });
        wrongButtonMenu.add(wrongButtonItem);
    }

    PlayButton(LoginForm lf) {
        loginForm = lf;
        button = new LocalizableButton();
        button.addActionListener(e -> {
            switch (state) {
                case CANCEL:
                    loginForm.stopLauncher();
                    break;
                default:
                    loginForm.startLauncher();
            }

        });
        button.setFont(getFont().deriveFont(Font.BOLD).deriveFont(LegacyLauncherFrame.getFontSize() * 1.5f));
        setCenter(button);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1)
                    wrongButtonMenu.show(PlayButton.this, mouseX, mouseY);
            }
        });
        setState(PlayButton.PlayButtonState.PLAY);
    }

    public PlayButton.PlayButtonState getState() {
        return state;
    }

    public void setState(PlayButton.PlayButtonState state) {
        if (state == null) {
            throw new NullPointerException();
        } else {
            this.state = state;
            button.setText(state.getPath());
            if (state == PlayButton.PlayButtonState.CANCEL) {
                setEnabled(true);
            }

        }
    }

    public void updateState() {
        VersionSyncInfo vs = loginForm.versions.getVersion();
        if (vs != null) {
            boolean installed = vs.isInstalled();
            boolean force = loginForm.checkbox.forceupdate.getState();
            if (!installed) {
                setState(PlayButton.PlayButtonState.INSTALL);
            } else {
                setState(force ? PlayButton.PlayButtonState.REINSTALL : PlayButton.PlayButtonState.PLAY);
            }

        }
    }

    public void loginStateChanged(LoginForm.LoginState state) {
        if (state == LoginForm.LoginState.LAUNCHING) {
            setState(PlayButton.PlayButtonState.CANCEL);
        } else {
            updateState();
            setEnabled(!Blocker.isBlocked(this));
        }

    }

    public void block(Object reason) {
        if (state != PlayButton.PlayButtonState.CANCEL) {
            setEnabled(false);
        }

    }

    public void unblock(Object reason) {
        setEnabled(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        button.setText(enabled ? state.getPath() : PlayButtonState.BLOCKED.getPath());
    }

    public enum PlayButtonState {
        REINSTALL("loginform.enter.reinstall"),
        INSTALL("loginform.enter.install"),
        PLAY("loginform.enter"),
        CANCEL("loginform.enter.cancel"),
        BLOCKED("loginform.enter.blocked");

        private final String path;

        PlayButtonState(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
