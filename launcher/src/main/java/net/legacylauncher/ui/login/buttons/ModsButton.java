package net.legacylauncher.ui.login.buttons;

import net.legacylauncher.ui.block.Unblockable;
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
 * Marked {@link Unblockable}, like the folder button next to it: the mod screen works on
 * what is already installed on disk, so there is no reason to grey it out while the
 * version list is still loading - which, on a slow or blocked connection, could be
 * forever. When no version is selected yet the screen says so itself.
 * <p>
 * Unlike its neighbours this button does not extend {@code LocalizableButton}: its label
 * comes from {@link ModrinthStrings}, which carries its own texts rather than reading
 * them from the lang files.
 */
public class ModsButton extends ExtendedButton implements Unblockable, LocalizableComponent {

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
}
