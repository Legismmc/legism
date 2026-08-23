package net.legacylauncher.ui;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.minecraft.auth.Account;
import net.legacylauncher.minecraft.crash.CrashManager;
import net.legacylauncher.minecraft.launcher.MinecraftException;
import net.legacylauncher.minecraft.launcher.MinecraftExtendedListener;
import net.legacylauncher.ui.alert.Alert;
import net.legacylauncher.ui.background.BackgroundManager;
import net.legacylauncher.ui.loc.Localizable;
import net.legacylauncher.ui.loc.LocalizableButton;
import net.legacylauncher.ui.loc.LocalizableComponent;
import net.legacylauncher.ui.progress.LaunchProgress;
import net.legacylauncher.ui.progress.ProgressBar;
import net.legacylauncher.ui.scenes.*;
import net.legacylauncher.ui.swing.DelayedComponent;
import net.legacylauncher.ui.swing.DelayedComponentLoader;
import net.legacylauncher.ui.swing.extended.ExtendedLayeredPane;
import net.legacylauncher.ui.swing.extended.ExtendedPanel;
import net.legacylauncher.util.OS;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MainPane extends ExtendedLayeredPane {
    private final LegacyLauncherFrame rootFrame;
    private static final int INPUT_SWALLOW_MS = 300;

    private final boolean repaintEveryTime;
    private PseudoScene scene;
    public final BackgroundManager background;
    public final DelayedComponent<LaunchProgress> progress;
    public final DefaultScene defaultScene;
    public final DelayedComponent<AccountManagerScene> accountManager;
    public final DelayedComponent<VersionManagerScene> versionManager;
    public final DelayedComponent<ModrinthScene> modrinthScene;
    public final DelayedComponent<InstancesScene> instancesScene;
    public final DelayedComponent<InstanceEditScene> instanceEditScene;
    public final DelayedComponent<RevertFontSize> revertFont;
    public final Map<String, DelayedComponent<? extends PseudoScene>> scenes = new HashMap<>();

    MainPane(final LegacyLauncherFrame frame) {
        super(null);
        rootFrame = frame;
        repaintEveryTime = OS.LINUX.isCurrent();
        log.trace("Creating background...");
        background = new BackgroundManager(this);
        add(background);
        log.trace("Init Default scene...");
        defaultScene = new DefaultScene(this);
        add(defaultScene);
        log.trace("Init Account manager scene...");
        accountManager = new DelayedComponent<>(new DelayedComponentLoader<AccountManagerScene>() {
            @Override
            public AccountManagerScene loadComponent() {
                return new AccountManagerScene(MainPane.this);
            }

            @Override
            public void onComponentLoaded(AccountManagerScene scene) {
                MainPane.this.add(scene);
                scene.onResize();
                scene.list.updateList();

                Account<?> selected = scene.list.getSelected();
                if (selected != null) {
                    MainPane.this.defaultScene.loginForm.accounts.setAccount(selected);
                }
            }
        });
        scenes.put("accounts", accountManager);
        //(accountManager);
        log.trace("Init Version manager scene...");
        versionManager = new DelayedComponent<>(new DelayedComponentLoader<VersionManagerScene>() {
            @Override
            public VersionManagerScene loadComponent() {
                return new VersionManagerScene(MainPane.this);
            }

            @Override
            public void onComponentLoaded(VersionManagerScene loaded) {
                MainPane.this.add(loaded);
                LegacyLauncher.getInstance().getVersionManager().asyncRefresh(true);
                loaded.onResize();
            }
        });
        scenes.put("versions", versionManager);
        //add(versionManager);
        log.trace("Init Modrinth scene...");
        modrinthScene = new DelayedComponent<>(new DelayedComponentLoader<ModrinthScene>() {
            @Override
            public ModrinthScene loadComponent() {
                return new ModrinthScene(MainPane.this);
            }

            @Override
            public void onComponentLoaded(ModrinthScene loaded) {
                MainPane.this.add(loaded);
                loaded.onResize();
            }
        });
        scenes.put("mods", modrinthScene);
        log.trace("Init Instances scene...");
        instancesScene = new DelayedComponent<>(new DelayedComponentLoader<InstancesScene>() {
            @Override
            public InstancesScene loadComponent() {
                return new InstancesScene(MainPane.this);
            }

            @Override
            public void onComponentLoaded(InstancesScene loaded) {
                MainPane.this.add(loaded);
                loaded.onResize();
            }
        });
        scenes.put("instances", instancesScene);
        instanceEditScene = new DelayedComponent<>(new DelayedComponentLoader<InstanceEditScene>() {
            @Override
            public InstanceEditScene loadComponent() {
                return new InstanceEditScene(MainPane.this);
            }

            @Override
            public void onComponentLoaded(InstanceEditScene loaded) {
                MainPane.this.add(loaded);
                loaded.onResize();
            }
        });
        scenes.put("instance-edit", instanceEditScene);
        progress = new DelayedComponent<>(new DelayedComponentLoader<LaunchProgress>() {
            @Override
            public LaunchProgress loadComponent() {
                return new LaunchProgress(frame);
            }

            @Override
            public void onComponentLoaded(LaunchProgress loaded) {
                MainPane.this.add(loaded);
                onResize();
            }
        });
        frame.getLauncher().getUiListeners().registerMinecraftLauncherListener(new MinecraftExtendedListener() {
            @Override
            public void onMinecraftCollecting() {
                progress.get().onMinecraftCollecting();
            }

            @Override
            public void onMinecraftComparingAssets(boolean fast) {
                progress.get().onMinecraftComparingAssets(fast);
            }

            @Override
            public void onMinecraftCheckingJre() {
                progress.get().onMinecraftCheckingJre();
            }

            @Override
            public void onMinecraftMalwareScanning() {
                progress.get().onMinecraftMalwareScanning();
            }

            @Override
            public void onMinecraftDownloading() {
                progress.get().onMinecraftDownloading();
            }

            @Override
            public void onMinecraftReconstructingAssets() {
                progress.get().onMinecraftReconstructingAssets();
            }

            @Override
            public void onMinecraftUnpackingNatives() {
                progress.get().onMinecraftUnpackingNatives();
            }

            @Override
            public void onMinecraftDeletingEntries() {
                progress.get().onMinecraftDeletingEntries();
            }

            @Override
            public void onMinecraftConstructing() {
                progress.get().onMinecraftConstructing();
            }

            @Override
            public void onMinecraftLaunch() {
                progress.get().onMinecraftLaunch();
            }

            @Override
            public void onMinecraftPostLaunch() {
                progress.get().onMinecraftPostLaunch();
            }

            @Override
            public void onMinecraftPrepare() {
                progress.get().onMinecraftPrepare();
            }

            @Override
            public void onMinecraftAbort() {
                progress.get().onMinecraftAbort();
            }

            @Override
            public void onMinecraftClose() {
                progress.get().onMinecraftClose();
            }

            @Override
            public void onMinecraftError(Throwable throwable) {
                progress.get().onMinecraftError(throwable);
            }

            @Override
            public void onMinecraftKnownError(MinecraftException exception) {
                progress.get().onMinecraftKnownError(exception);
            }

            @Override
            public void onCrashManagerInit(CrashManager manager) {
                progress.get().onCrashManagerInit(manager);
            }
        });
        //add(progress);
        revertFont = new DelayedComponent<>(new DelayedComponentLoader<RevertFontSize>() {
            @Override
            public RevertFontSize loadComponent() {
                return new RevertFontSize();
            }

            @Override
            public void onComponentLoaded(RevertFontSize loaded) {
                MainPane.this.add(loaded);
                onResize();
            }
        });

        if (shouldShowRevertFont())
            revertFont.load();

        setScene(defaultScene, false);
        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                onResize();
            }
        });
    }

    public PseudoScene getScene() {
        return scene;
    }

    /**
     * Swallows mouse input for a moment right after a scene change.
     * <p>
     * The click that switches scenes is two events: the press opens the new scene, and the
     * release arrives once it is built. By then the button that was pressed is hidden along
     * with its scene, so AWT retargets the release to whatever the new scene happens to put
     * under the cursor - pressing it. Building a scene for the first time takes long enough
     * for a perfectly normal click to trigger this, so a transparent glass pane eats the
     * leftovers.
     */
    private void swallowInputBriefly() {
        JRootPane root = getRootPane();
        if (root == null) {
            return;
        }
        final Component previous = root.getGlassPane();
        final JPanel blocker = new JPanel();
        blocker.setOpaque(false);
        blocker.addMouseListener(new MouseAdapter() {
        });
        blocker.addMouseMotionListener(new MouseMotionAdapter() {
        });
        root.setGlassPane(blocker);
        blocker.setVisible(true);

        Timer timer = new Timer(INPUT_SWALLOW_MS, e -> {
            blocker.setVisible(false);
            root.setGlassPane(previous);
        });
        timer.setRepeats(false);
        timer.start();
    }

    public void setScene(PseudoScene scene) {
        setScene(scene, true);
    }

    public void setScene(DelayedComponent<? extends PseudoScene> scene) {
        setScene(scene.get());
    }

    public void setScene(PseudoScene newscene, boolean animate) {
        if (newscene == null) {
            throw new NullPointerException();
        } else if (!newscene.equals(scene)) {
            Component[] var6;
            int var5 = (var6 = getComponents()).length;

            for (int var4 = 0; var4 < var5; ++var4) {
                Component comp = var6[var4];
                if (!comp.equals(newscene) && comp instanceof PseudoScene) {
                    ((PseudoScene) comp).setShown(false, animate);
                }
            }

            scene = newscene;
            scene.setShown(true);
            swallowInputBriefly();
            if (repaintEveryTime) {
                repaint();
            }

        }
    }

    public void openDefaultScene() {
        setScene(defaultScene);
    }

    public void openAccountEditor() {
        setScene(accountManager.get());
    }

    public void openVersionManager() {
        setScene(versionManager.get());
    }

    public void openInstancesScene() {
        InstancesScene scene = instancesScene.get();
        scene.onResize();
        setScene(scene);
        scene.panel.onShown();
    }

    /**
     * Opens the instance list and immediately asks for a new instance - the shortcut on
     * the toolbar's context menu.
     */
    public void openInstancesSceneAndCreate() {
        openInstancesScene();
        instancesScene.get().panel.createInstance();
    }

    public void openInstanceEditor(net.legacylauncher.instance.Instance instance) {
        InstanceEditScene scene = instanceEditScene.get();
        scene.panel.setInstance(instance);
        scene.onResize();
        setScene(scene);
        scene.panel.onShown();
    }

    public void openModrinthScene() {
        ModrinthScene mods = modrinthScene.get();
        mods.onResize();
        setScene(mods);
        // PseudoScene.setShown is a no-op the first time a scene is opened, so the panel
        // is refreshed from here rather than from an onShown hook
        mods.panel.onShown();
    }

    public LegacyLauncherFrame getRootFrame() {
        return rootFrame;
    }

    public DelayedComponent<LaunchProgress> getProgress() {
        return progress;
    }

    public void onResize() {
        if (progress.isLoaded()) {
            progress.get().setBounds(0, getHeight() - ProgressBar.DEFAULT_HEIGHT + 1, getWidth(), ProgressBar.DEFAULT_HEIGHT);
        }
        if (revertFont.isLoaded()) {
            revertFont.get().setBounds(0, 0, getWidth(), getFontMetrics(revertFont.get().revertButton.getFont()).getHeight() * 3);
        }
        //service.onResize();
    }

    public Point getLocationOf(Component comp) {
        Point compLocation = comp.getLocationOnScreen();
        Point paneLocation = getLocationOnScreen();
        return new Point(compLocation.x - paneLocation.x, compLocation.y - paneLocation.y);
    }

    private boolean shouldShowRevertFont() {
        float size = (float) rootFrame.getConfiguration().getInteger("gui.font.old");
        if (size < LegacyLauncherFrame.minFontSize || size > LegacyLauncherFrame.maxFontSize)
            size = LegacyLauncherFrame.maxFontSize;
        float oldSize = size;
        return LegacyLauncherFrame.getFontSize() != oldSize;
    }

    public class RevertFontSize extends ExtendedPanel implements LocalizableComponent {
        private final LocalizableButton revertButton, closeButton;
        private final int oldSizeInt;

        private RevertFontSize() {
            float size = (float) rootFrame.getConfiguration().getInteger("gui.font.old");

            if (size < LegacyLauncherFrame.minFontSize || size > LegacyLauncherFrame.maxFontSize)
                size = LegacyLauncherFrame.maxFontSize;

            float oldSize = size;
            oldSizeInt = (int) size;

            revertButton = new LocalizableButton("revert.font.approve");
            revertButton.setFont(revertButton.getFont().deriveFont(size));
            revertButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    rootFrame.getConfiguration().set("gui.font", rootFrame.getConfiguration().getInteger("gui.font.old"), false);

                    Alert.showLocMessage("revert.font.approved");

                    closeButton.doClick();
                }
            });

            closeButton = new LocalizableButton("revert.font.close");
            closeButton.setToolTipText("revert.font.close.hint");
            closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, size));
            closeButton.addActionListener(e -> {
                rootFrame.getConfiguration().set("gui.font.old", rootFrame.getConfiguration().getInteger("gui.font"));
                MainPane.this.remove(RevertFontSize.this);
                MainPane.this.repaint();
            });

            add(new JComponent[]{revertButton, closeButton});

            updateLocale();
        }

        @Override
        public void updateLocale() {
            Localizable.updateContainer(this);
        }
    }
}
