package net.legacylauncher.ui.login;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.managers.ProfileManager;
import net.legacylauncher.managers.ProfileManagerListener;
import net.legacylauncher.managers.SwingProfileManagerListener;
import net.legacylauncher.minecraft.auth.Account;
import net.legacylauncher.minecraft.auth.Authenticator;
import net.legacylauncher.minecraft.auth.AuthenticatorDatabase;
import net.legacylauncher.minecraft.auth.AuthenticatorListener;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.block.Blockable;
import net.legacylauncher.ui.listener.AuthUIListener;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.swing.AccountCellRenderer;
import net.legacylauncher.ui.swing.extended.ExtendedComboBox;
import net.legacylauncher.user.InvalidCredentialsException;
import net.legacylauncher.user.User;
import net.minecraft.launcher.versions.ReleaseType;

import javax.swing.*;
import java.io.IOException;
import java.util.Collection;

@Slf4j
public class AccountComboBox extends ExtendedComboBox<Account<? extends User>> implements Blockable, LoginForm.LoginProcessListener, ProfileManagerListener, LocalizableComponent {
    private static final long serialVersionUID = 6618039863712810645L;
    private static final Account<? extends User> EMPTY = AccountCellRenderer.EMPTY;
    private static final Account<? extends User> MANAGE = AccountCellRenderer.MANAGE;
    private final ProfileManager manager;
    private final LoginForm loginForm;
    private final AuthenticatorListener<? super User> listener;
    private final MutableComboBoxModel<Account<? extends User>> model;
    private Account<? extends User> selectedAccount;
    boolean refreshing;

    AccountComboBox(LoginForm lf) {
        super(new AccountCellRenderer());
        loginForm = lf;
        model = getMutableModel();
        manager = LegacyLauncher.getInstance().getProfileManager();
        manager.addListener(new SwingProfileManagerListener(this));
        listener = new AuthUIListener<User>(lf) {
            @Override
            public void onAuthPassingError(Authenticator<? extends User> auth, Throwable e) {
                if (e instanceof InvalidCredentialsException) {
                    //TLauncher.getInstance().getProfileManager().getAccountManager().getUserSet().remove(selectedAccount.getUser());
                    loginForm.pane.openAccountEditor();
                    loginForm.pane.accountManager.get().multipane.showTip("add-account-" +
                            (selectedAccount.getType() == Account.AccountType.ELY_LEGACY ? "ely" :
                                    selectedAccount.getType().name().toLowerCase(java.util.Locale.ROOT))
                    );
                }
                super.onAuthPassingError(auth, e);
            }
        };
        addItemListener(e -> {
            Account<? extends User> selected = (Account<? extends User>) getSelectedItem();
            if (selected != null && selected != AccountComboBox.EMPTY) {
                if (selected == AccountComboBox.MANAGE) {
                    if (selectedAccount != null && loginForm.pane.accountManager.isLoaded()) {
                        loginForm.pane.accountManager.get().list.select(selectedAccount);
                    }
                    loginForm.pane.openAccountEditor();
                    setSelectedIndex(0);
                } else {
                    selectedAccount = selected;
                    // fireRefresh must stay false while a refreshAccounts() pass is what
                    // triggered this selection change, or the two would recurse forever;
                    // for a genuine pick from the dropdown it has to be true, or nothing
                    // outside this combo box - the instances toolbar included - ever
                    // learns the active account changed
                    LegacyLauncher.getInstance().getProfileManager().getAccountManager().getUserSet().select(selectedAccount == null ? null : selectedAccount.getUser(), !refreshing);
                    try {
                        LegacyLauncher.getInstance().getProfileManager().saveProfiles();
                    } catch (IOException e1) {
                        log.warn("Could not save profiles", e1);
                    }
                }
            }
            updateAccount();
        });
    }

    private void updateAccount() {
        Account.AccountType type = Account.AccountType.PLAIN;
        if (!refreshing) {
            if (selectedAccount != null) {
                if (selectedAccount.getType() == Account.AccountType.ELY ||
                        selectedAccount.getType() == Account.AccountType.ELY_LEGACY) {
                    if (!loginForm.tlauncher.getLibraryManager().isRefreshing()) {
                        loginForm.tlauncher.getLibraryManager().asyncRefresh();
                    }
                }
                type = selectedAccount.getType();
            }
        }
        VersionComboBox.showVersionForType = type;
        loginForm.versions.comboBoxFilter.updateState();
    }

    public Account<? extends User> getAccount() {
        Account<? extends User> value = (Account<? extends User>) getSelectedItem();
        return value != null && !value.equals(EMPTY) ? value : null;
    }

    public void setAccount(Account<? extends User> account) {
        if (account != null) {
            if (!account.equals(getAccount())) {
                setSelectedItem(account);
            }
        }
    }

    /**
     * Deselects the active account entirely, so nothing is picked to play with until one is
     * chosen again - the account list's own item listener only reacts to picking a real
     * account or "Manage...", so clearing the selection has to go through the same
     * {@code UserSet} call directly instead.
     * <p>
     * {@code fireRefresh} has to stay false here: a true one would have {@link
     * #refreshAccounts} rebuild the model from scratch mid-call, and a combo box model
     * auto-selects the first item whenever a list goes from empty to non-empty - silently
     * reselecting whichever account happens to be first, right back out from under the very
     * clear this method is trying to do.
     * <p>
     * The selection is cleared with {@code setSelectedItem(null)}, not {@link #EMPTY}: a
     * non-editable {@link javax.swing.JComboBox} silently ignores any selection request for
     * a value that is not actually in its model, and {@code EMPTY} is only ever added there
     * when the account list is empty to begin with - so passing it here while real accounts
     * are still in the model would just be dropped on the floor. {@code null} bypasses that
     * membership check entirely, and {@link #getAccount} already treats a null selection the
     * same way it treats {@code EMPTY}.
     */
    public void clearAccount() {
        refreshing = true;
        try {
            selectedAccount = null;
            LegacyLauncher.getInstance().getProfileManager().getAccountManager().getUserSet().select(null, false);
            try {
                LegacyLauncher.getInstance().getProfileManager().saveProfiles();
            } catch (IOException e) {
                log.warn("Could not save profiles", e);
            }
            setSelectedItem(null);
        } finally {
            refreshing = false;
        }
        updateAccount();
    }

    public void loggingIn() throws LoginException {
        if (loginForm.versions.getVersion() != null &&
                loginForm.versions.getVersion().getAvailableVersion().getReleaseType() == ReleaseType.LAUNCHER) {
            return;
        }
        final Account<? extends User> account = getAccount();
        if (account == null) {
            loginForm.pane.openAccountEditor();
            Alert.showLocError("account.empty.error");
            throw new LoginException("Account list is empty!");
        } else if (!account.isFree()) {
            throw new LoginWaitException("Waiting for auth...", () -> Authenticator.instanceFor(account).pass(listener));
        }
    }

    public void loginFailed() {
    }

    public void loginSucceed() {
    }

    public void refreshAccounts(AuthenticatorDatabase db, Account<? extends User> select) {
        removeAllItems();
        selectedAccount = null;
        Collection<Account<? extends User>> list1 = db.getAccounts();
        if (list1.isEmpty()) {
            addItem(EMPTY);
        } else {
            refreshing = true;
            list1.forEach(model::addElement);

            for (Account<? extends User> account1 : list1) {
                if (select != null && select.equals(account1)) {
                    setSelectedItem(account1);
                    break;
                }
            }

            refreshing = false;
        }
        addItem(MANAGE);
        updateAccount();
    }

    public void updateLocale() {
        refreshAccounts(manager.getAuthDatabase(), getSelectedValue());
    }

    public void onAccountsRefreshed(AuthenticatorDatabase db) {
        refreshAccounts(db, null);
    }

    public void onProfilesRefreshed(ProfileManager pm) {
        refreshAccounts(pm.getAuthDatabase(), pm.getAccountManager().getUserSet().getSelected() == null ? null : new Account<>(pm.getAccountManager().getUserSet().getSelected()));
    }

    public void onProfileManagerChanged(ProfileManager pm) {
        refreshAccounts(pm.getAuthDatabase(), pm.getAccountManager().getUserSet().getSelected() == null ? null : new Account<>(pm.getAccountManager().getUserSet().getSelected()));
    }

    public void block(Object reason) {
        setEnabled(false);
    }

    public void unblock(Object reason) {
        setEnabled(true);
    }
}
