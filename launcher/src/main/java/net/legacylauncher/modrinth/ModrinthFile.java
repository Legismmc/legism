package net.legacylauncher.modrinth;

import java.util.Map;

/**
 * One downloadable file attached to a {@link ModrinthVersion}.
 */
public class ModrinthFile {
    private Map<String, String> hashes;
    private String url;
    private String filename;
    private boolean primary;
    private long size;

    public String getUrl() {
        return url;
    }

    public String getFilename() {
        return filename;
    }

    public boolean isPrimary() {
        return primary;
    }

    public long getSize() {
        return size;
    }

    public String getSha512() {
        return hashes == null ? null : hashes.get("sha512");
    }

    public String getSha1() {
        return hashes == null ? null : hashes.get("sha1");
    }

    @Override
    public String toString() {
        return "ModrinthFile{" + filename + "}";
    }
}
