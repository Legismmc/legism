package net.legacylauncher.server;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Downloads a resolved {@link ServerCoreDownload} into a server's folder as
 * {@code server.jar}, verifying it against whatever hash the core published.
 * <p>
 * Blocks on network I/O, so callers must stay off the Swing thread.
 */
@Slf4j
public final class ServerCoreInstaller {
    private ServerCoreInstaller() {
    }

    public static void install(ServerInstance server, ServerCoreDownload download) throws IOException {
        log.info("Downloading {} for {}", download.getUrl(), server);
        Content content = EHttpClient.toContent(
                Request.get(download.getUrl()).addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT)
        );
        if (content == null) {
            throw new IOException("no content received for " + download.getUrl());
        }
        byte[] bytes = content.asBytes();
        verify(download, bytes);

        File destination = server.getJarFile();
        File temp = new File(destination.getParentFile(), destination.getName() + ".part");
        try {
            Files.write(temp.toPath(), bytes);
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            temp.delete();
        }
        log.info("Installed {} ({} bytes) for {}", destination, bytes.length, server);
    }

    private static void verify(ServerCoreDownload download, byte[] bytes) throws IOException {
        if (StringUtils.isEmpty(download.getHash()) || StringUtils.isEmpty(download.getHashAlgorithm())) {
            log.warn("No hash published for {}, installing unverified", download.getFileName());
            return;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(download.getHashAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(download.getHashAlgorithm() + " is required by the Java platform", e);
        }
        byte[] hash = digest.digest(bytes);
        StringBuilder actual = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                actual.append('0');
            }
            actual.append(hex);
        }
        if (!download.getHash().equalsIgnoreCase(actual.toString())) {
            throw new IOException(download.getFileName() + " does not match the published "
                    + download.getHashAlgorithm() + " hash (expected " + download.getHash()
                    + ", got " + actual + ")");
        }
    }
}
