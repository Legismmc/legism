package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.ui.block.Blockable;
import net.legacylauncher.ui.images.Images;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.login.LoginForm;
import net.legacylauncher.ui.modrinth.ModrinthStrings;
import net.legacylauncher.ui.swing.extended.ExtendedButton;
import net.legacylauncher.util.SwingUtil;

import java.awt.Insets;

/**
 * Opens the Modrinth mod browser for the version selected in the login form.
 * <p>
 * Unlike its neighbours this button does not extend {@code LocalizableButton}: its label
 * comes from {@link ModrinthStrings}, which carries its own texts rather than reading
 * them from the lang files.
 */
public class ModsButton extends ExtendedButton implements Blockable, LocalizableComponent {

    ModsButton(LoginForm loginForm) {
        setIcon(Images.getIcon24("puzzle-piece"));
        addActionListener(e -> loginForm.pane.openModrinthScene());
        updateLocale();
    }

    @Override
    public Insets getInsets() {
        return SwingUtil.magnify(super.getInsets());
    }

    @Override
    public void updateLocale() {
        setToolTipText(ModrinthStrings.get("title"));
    }

    @Override
    public void block(Object reason) {
        setEnabled(false);
    }

    @Override
    public void unblock(Object reason) {
        setEnabled(true);
    }
}
