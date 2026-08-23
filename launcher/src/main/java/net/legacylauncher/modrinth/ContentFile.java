package net.legacylauncher.modrinth;

/**
 * A single downloadable file, with whatever hash its library publishes for it.
 */
public class ContentFile {
    private final String fileName;
    private final String url;
    private final long size;
    private final String sha512;
    private final String sha1;
    private final String md5;

    public ContentFile(String fileName, String url, long size, String sha512, String sha1, String md5) {
        this.fileName = fileName;
        this.url = url;
        this.size = size;
        this.sha512 = sha512;
        this.sha1 = sha1;
        this.md5 = md5;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * Where to fetch it. May be {@code null}: CurseForge withholds the link when a mod
     * author has opted out of third-party downloads.
     */
    public String getUrl() {
        return url;
    }

    public long getSize() {
        return size;
    }

    public String getSha512() {
        return sha512;
    }

    public String getSha1() {
        return sha1;
    }

    public String getMd5() {
        return md5;
    }

    @Override
    public String toString() {
        return "ContentFile{" + fileName + "}";
    }
}
