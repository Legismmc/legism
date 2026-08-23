package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.ui.block.Blockable;
import net.legacylauncher.ui.block.Blocker;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableButton;
import net.legacylauncher.ui.loc.LocalizableMenuItem;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.scenes.DefaultScene;
import net.legacylauncher.util.SwingUtil;

import javax.swing.*;
import java.awt.*;

import static net.legacylauncher.util.SwingUtil.updateUINullable;

public class SettingsButton extends LocalizableButton implements Blockable {
    private final LoginForm lf;
    private final JPopupMenu popup;
    private final LocalizableMenuItem accountManager;
    private final LocalizableMenuItem versionManager;
    private final JMenuItem mods;
    private final JMenuItem instances;

    SettingsButton(LoginForm loginform) {
        lf = loginform;
        setToolTipText("loginform.button.settings");
        setIcon(Images.getIcon24("bars"));
        popup = new JPopupMenu();
        LocalizableMenuItem settings = new LocalizableMenuItem("loginform.button.settings.launcher");
        settings.addActionListener(e -> lf.scene.setSidePanel(DefaultScene.SidePanel.SETTINGS));
        popup.add(settings);
        versionManager = new LocalizableMenuItem("loginform.button.settings.version");
        versionManager.addActionListener(e -> lf.pane.openVersionManager());
        popup.add(versionManager);
        accountManager = new LocalizableMenuItem("loginform.button.settings.account");
        accountManager.addActionListener(e -> lf.pane.openAccountEditor());
        popup.add(accountManager);
        mods = new JMenuItem(ModrinthStrings.get("title"));
        mods.setIcon(Images.getIcon16("puzzle-piece"));
        mods.addActionListener(e -> lf.pane.openModrinthScene());
        popup.add(mods);
        instances = new JMenuItem(ModrinthStrings.get("instances.title"));
        instances.setIcon(Images.getIcon16("cube"));
        instances.addActionListener(e -> lf.pane.openInstancesScene());
        popup.add(instances);
        setPreferredSize(new Dimension(30, getHeight()));
        addActionListener(e -> callPopup());
    }

    public Insets getInsets() {
        return SwingUtil.magnify(super.getInsets());
    }

    void callPopup() {
        lf.defocus();
        popup.show(this, 0, getHeight());
    }

    public void block(Object reason) {
        if (reason.equals("auth") || reason.equals("launch")) {
            Blocker.blockComponents(reason, accountManager, versionManager);
        }

    }

    public void unblock(Object reason) {
        Blocker.unblockComponents(reason, accountManager, versionManager);
    }

    @Override
    public void updateLocale() {
        super.updateLocale();
        if (mods != null) { // may run before the constructor finished
            mods.setText(ModrinthStrings.get("title"));
            instances.setText(ModrinthStrings.get("instances.title"));
        }
    }

    @Override
    public void updateUI() {
        updateUINullable(popup);
        super.updateUI();
    }
}
