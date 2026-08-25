package net.legacylauncher.server;

/**
 * Where to download a resolved server core jar from, and how to check it once downloaded.
 */
public class ServerCoreDownload {
    private final String url;
    private final String fileName;
    private final String hashAlgorithm;
    private final String hash;

    public ServerCoreDownload(String url, String fileName, String hashAlgorithm, String hash) {
        this.url = url;
        this.fileName = fileName;
        this.hashAlgorithm = hashAlgorithm;
        this.hash = hash;
    }

    public String getUrl() {
        return url;
    }

    public String getFileName() {
        return fileName;
    }

    /**
     * {@code null} when the core did not publish a hash for this build.
     */
    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public String getHash() {
        return hash;
    }
}
