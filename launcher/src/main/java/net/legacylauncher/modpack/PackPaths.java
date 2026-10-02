package net.legacylauncher.modpack;

import java.io.File;
import java.io.IOException;

/**
 * Pack paths come from other people's metadata, so every one is treated as hostile until
 * it has been shown to stay inside the folder it is meant for.
 */
public final class PackPaths {
    private PackPaths() {
    }

    /**
     * Forward slashes, no leading {@code ./} or {@code /}, no trailing slash. {@code null}
     * becomes {@code ""}.
     */
    public static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String result = path.replace('\\', '/');
        while (result.startsWith("./")) {
            result = result.substring(2);
        }
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.equals(".")) {
            return "";
        }
        return result;
    }

    public static String join(String folder, String name) {
        String a = normalize(folder);
        String b = normalize(name);
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + "/" + b;
    }

    /**
     * @return {@code relative} resolved against {@code root}
     * @throws IOException when it would end up anywhere outside {@code root}
     */
    public static File resolve(File root, String relative) throws IOException {
        String normalized = normalize(relative);
        if (normalized.isEmpty()) {
            throw new IOException("empty path");
        }
        File canonicalRoot = root.getCanonicalFile();
        File destination = new File(canonicalRoot, normalized).getCanonicalFile();
        if (!destination.toPath().startsWith(canonicalRoot.toPath()) || destination.equals(canonicalRoot)) {
            throw new IOException("refusing to write outside the instance folder: " + relative);
        }
        return destination;
    }

    /**
     * @return {@code file}'s path below {@code root}, with forward slashes
     */
    public static String relativize(File root, File file) throws IOException {
        return normalize(root.getCanonicalFile().toPath().relativize(file.getCanonicalFile().toPath()).toString());
    }

    public static boolean startsWithAny(String path, Iterable<String> prefixes) {
        for (String prefix : prefixes) {
            if (!prefix.isEmpty() && (path.equals(normalize(prefix)) || path.startsWith(normalize(prefix) + "/")
                    || path.startsWith(prefix))) {
                return true;
            }
        }
        return false;
    }
}
