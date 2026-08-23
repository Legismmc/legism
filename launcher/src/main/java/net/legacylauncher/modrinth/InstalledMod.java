package net.legacylauncher.modrinth;

import java.io.File;

/**
 * A jar sitting in the game's {@code mods} directory.
 * <p>
 * Minecraft loads every {@code .jar} in that directory; renaming one to
 * {@code .jar.disabled} is the conventional way of keeping it around without loading it,
 * and is what {@link ModInstaller#setEnabled} does.
 */
public class InstalledMod {
    public static final String DISABLED_SUFFIX = ".disabled";

    private final File file;

    public InstalledMod(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public boolean isEnabled() {
        return !file.getName().endsWith(DISABLED_SUFFIX);
    }

    /**
     * The file name without the {@code .disabled} marker, so enabling and disabling a mod
     * does not make it jump around the list.
     */
    public String getDisplayName() {
        String name = file.getName();
        if (name.endsWith(DISABLED_SUFFIX)) {
            return name.substring(0, name.length() - DISABLED_SUFFIX.length());
        }
        return name;
    }

    public long getSize() {
        return file.length();
    }

    @Override
    public String toString() {
        return "InstalledMod{" + file.getName() + "}";
    }
}
