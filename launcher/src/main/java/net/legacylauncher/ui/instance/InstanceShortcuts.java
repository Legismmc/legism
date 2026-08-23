package net.legacylauncher.ui.instance;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.Instance;
import net.legacylauncher.instance.InstanceManager;
import net.legacylauncher.util.OS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Desktop shortcuts that start one instance directly.
 * <p>
 * A plain Windows {@code .lnk} would be the obvious choice, but the portable launcher's
 * stub passes any command line it is given straight to the JVM, so a shortcut cannot hand
 * the instance name to the launcher that way. A tiny script that exports
 * {@code LL_INSTANCE} and then starts the launcher does the job with no such trouble - the
 * launcher reads that variable on startup.
 */
@Slf4j
public final class InstanceShortcuts {
    /**
     * Environment variable the launcher checks on startup to auto-start an instance.
     */
    public static final String ENV_VARIABLE = InstanceManager.ENV_INSTANCE;

    private InstanceShortcuts() {
    }

    /**
     * Finds the executable that started this launcher, so the shortcut can point at it.
     *
     * @return {@code null} when it cannot be worked out, e.g. when running from an IDE
     */
    public static File findLauncherExecutable() {
        String restartExec = System.getProperty("tlauncher.bootstrap.restartExec");
        if (restartExec != null && !restartExec.isEmpty()) {
            File candidate = new File(restartExec).getAbsoluteFile();
            if (candidate.isFile()) {
                return candidate;
            }
        }
        // the portable build runs from the folder holding the executable
        File working = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        for (String name : new String[]{"LL.exe", "LegacyLauncher.exe", "launcher.exe"}) {
            File candidate = new File(working, name);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    public static File defaultShortcutDir() {
        File desktop = new File(System.getProperty("user.home", "."), "Desktop");
        return desktop.isDirectory() ? desktop : new File(System.getProperty("user.home", "."));
    }

    public static String suggestedName(Instance instance) {
        String base = instance.getName().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (base.isEmpty()) {
            base = instance.getId();
        }
        return base + (OS.WINDOWS.isCurrent() ? ".cmd" : ".sh");
    }

    /**
     * Writes the shortcut script.
     *
     * @return the file that was actually written, extension included
     */
    public static File write(File chosen, File launcherExe, Instance instance) throws IOException {
        File destination = withExtension(chosen);
        String script = OS.WINDOWS.isCurrent()
                ? windowsScript(launcherExe, instance)
                : unixScript(launcherExe, instance);

        Charset charset = OS.WINDOWS.isCurrent() ? Charset.forName("windows-1251") : Charset.forName("UTF-8");
        Files.write(destination.toPath(), script.getBytes(charset));
        if (!OS.WINDOWS.isCurrent()) {
            //noinspection ResultOfMethodCallIgnored
            destination.setExecutable(true, false);
        }
        log.info("Wrote a shortcut for {} to {}", instance, destination);
        return destination;
    }

    private static File withExtension(File chosen) {
        String wanted = OS.WINDOWS.isCurrent() ? ".cmd" : ".sh";
        if (chosen.getName().toLowerCase(Locale.ROOT).endsWith(wanted)) {
            return chosen;
        }
        return new File(chosen.getParentFile(), chosen.getName() + wanted);
    }

    private static String windowsScript(File launcherExe, Instance instance) {
        return "@echo off\r\n"
                + "rem Starts the \"" + instance.getName() + "\" instance\r\n"
                + "set " + ENV_VARIABLE + "=" + instance.getId() + "\r\n"
                + "cd /d \"" + launcherExe.getParent() + "\"\r\n"
                + "start \"\" \"" + launcherExe.getAbsolutePath() + "\"\r\n";
    }

    private static String unixScript(File launcherExe, Instance instance) {
        return "#!/bin/sh\n"
                + "# Starts the \"" + instance.getName() + "\" instance\n"
                + "cd \"" + launcherExe.getParent() + "\" || exit 1\n"
                + ENV_VARIABLE + "=" + instance.getId() + " exec \"" + launcherExe.getAbsolutePath() + "\"\n";
    }
}
