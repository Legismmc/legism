package net.legacylauncher.configuration;

import net.legacylauncher.managers.GPUManager;
import net.legacylauncher.managers.JavaManagerConfig;
import net.legacylauncher.ui.FlatLaf;
import net.legacylauncher.util.Direction;
import net.legacylauncher.util.IntegerArray;
import net.legacylauncher.util.MinecraftUtil;
import net.legacylauncher.util.OS;
import net.legacylauncher.util.shared.JavaVersion;
import net.minecraft.launcher.versions.ReleaseType;

import java.lang.ref.WeakReference;
import java.util.*;

public final class ConfigurationDefaults {
    private static WeakReference<ConfigurationDefaults> ref;

    public static ConfigurationDefaults getInstance() {
        ConfigurationDefaults instance;

        if (ref == null || (instance = ref.get()) == null) {
            instance = new ConfigurationDefaults();
            ref = new WeakReference<>(instance);
        }

        return instance;
    }

    private static final int VERSION = 3;
    private final HashMap<String, Object> d = new HashMap<>();

    private ConfigurationDefaults() {
        d.put("settings.version", VERSION);

        d.put("minecraft.gamedir", MinecraftUtil.getDefaultWorkingDirectory().getAbsolutePath());
        d.put("minecraft.gamedir.separate", Configuration.SeparateDirs.NONE.name().toLowerCase(Locale.ROOT));

        d.put("minecraft.size", new IntegerArray(925, 530));
        d.put("minecraft.fullscreen", false);

        for (ReleaseType type : ReleaseType.getDefault()) {
            d.put("minecraft.versions." + type.name().toLowerCase(java.util.Locale.ROOT), true);
        }
        d.put("minecraft.versions.sub." + ReleaseType.SubType.REMOTE.name().toLowerCase(java.util.Locale.ROOT), true);
        d.put("minecraft.versions.sub." + ReleaseType.SubType.OLD_RELEASE.name().toLowerCase(java.util.Locale.ROOT), true);
        d.put("minecraft.versions.only-installed", false);

        d.put("minecraft.jre.type", JavaManagerConfig.Recommended.TYPE);

        d.put("minecraft.javaargs", null);
        d.put("minecraft.args", null);
        d.put("minecraft.improvedargs", true);
        d.put("minecraft.gpu", GPUManager.GPU.DISCRETE.getName());
        if (OS.LINUX.isCurrent()) {
            d.put("minecraft.gamemode", true);
        }

        d.put("minecraft.xmx", "auto");

        d.put("minecraft.onlaunch", Configuration.ActionOnLaunch.HIDE);

        // Issued to this app (registered with CurseForge under its former name,
        // "Legacy by tgsko") by CurseForge's 3rd Party API program - meant to be shipped
        // with the app, not per user, so every install works out of the box. A user's own
        // key in Settings still overrides this.
        d.put("curseforge.apikey", "$2a$10$U/ik1JVOXeYmLxnqIfUcxOsKvrogh/gpVqpL9ra6cmw12qzKP4gli");

        d.put("discord.rpc.enabled", true);
        // The fork's own Discord application, so Rich Presence works without anyone
        // registering one of their own. An application id is public by design - it is
        // handed to every client that connects - so shipping it costs nothing. A user's
        // own id in Settings still overrides this.
        d.put("discord.rpc.client-id", "1543596924299509830");

        // Following the system proxy stays the default, but it is now a setting rather
        // than something only reachable by editing tl.bootargs by hand - a machine that
        // advertises a proxy which never answers used to leave the launcher unable to
        // reach anything, with no way out from inside it.
        d.put("connection.proxy.mode", Configuration.ProxyMode.SYSTEM);
        d.put("connection.proxy.type", "http");
        d.put("connection.proxy.host", "");
        d.put("connection.proxy.port", "");
        d.put("connection.proxy.username", "");
        d.put("connection.proxy.password", "");

        d.put("minecraft.crash", true);
        d.put("minecraft.mods.removeUndesirable", true);

        d.put("gui.font", 12);
        d.put("gui.size", new IntegerArray(1000, 600));
//        d.put("gui.systemlookandfeel", false);

        d.putAll(FlatLaf.getDefaults());

        d.put("gui.uitheme", Configuration.UiTheme.getDefault());

        d.put("gui.background", null);

        d.put("gui.logger", Configuration.LoggerType.getDefault());
        d.put("gui.logger.width", 720);
        d.put("gui.logger.height", 500);
        d.put("gui.logger.x", 30);
        d.put("gui.logger.y", 30);

        d.put("gui.direction.loginform", Direction.CENTER);

        d.put("client", UUID.randomUUID());

        d.put("connection.ssl", true);

        if (OS.WINDOWS.isCurrent()) {
            d.put("windows.dxdiag", true);
            d.put("windows.gpuperf", true);
        }

        d.put("bootstrap.switchToBeta", false);

        d.put("experiments.enabled", "none");

        d.put("minecraft.deletePatchy", true);
    }

    public static int getVersion() {
        return 3;
    }

    public Map<String, Object> getMap() {
        return Collections.unmodifiableMap(d);
    }

    public Object get(String key) {
        return d.get(key);
    }
}
