package net.legacylauncher.modpack;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.instance.ModpackImporter;
import net.legacylauncher.instance.ModpackImporter.CancelledException;
import net.legacylauncher.util.EHttpClient;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * Network access for modpack installs: files streamed to disk with progress, a hash check
 * and retries, and small API answers read as text with the same retries.
 * <p>
 * A pack is hundreds - for FTB, thousands - of downloads in a row, and on an ordinary home
 * connection one of them dropping is close to certain. Every request here is therefore
 * tried a few times before it counts as failed.
 */
@Slf4j
public final class PackDownloads {
    private static final int ATTEMPTS = 3;

    /**
     * Without a read timeout a connection that stalls - rather than drops - just sits
     * there, and the install looks frozen forever instead of retrying.
     */
    @SuppressWarnings("deprecation") // still the only per-request way to set it in this client version
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(20))
            .setConnectionRequestTimeout(Timeout.ofSeconds(30))
            .setResponseTimeout(Timeout.ofSeconds(60))
            .build();

    private PackDownloads() {
    }

    /**
     * Downloads to {@code destination}, trying each mirror in turn and each one a few
     * times. The file only appears under its real name once it has fully arrived and
     * matches its hash, so a failed or cancelled download never leaves a broken file.
     *
     * @param hashAlgorithm a {@link MessageDigest} algorithm, or {@code null} for no check
     * @param size          the expected size, or anything not positive when unknown; only
     *                      used to show progress when the server does not say
     */
    public static void fetch(List<String> urls, File destination, String hashAlgorithm, String hash, long size,
                             ModpackImporter.ProgressListener listener) throws IOException {
        if (urls == null || urls.isEmpty()) {
            throw new IOException("no download link for " + destination.getName());
        }
        IOException last = null;
        for (String url : urls) {
            try {
                fetchWithRetries(url, destination, hashAlgorithm, hash, size, listener);
                return;
            } catch (CancelledException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                log.warn("Could not download {} from {}, trying the next mirror if any", destination.getName(), url);
            }
        }
        throw last;
    }

    public static void fetch(String url, File destination, String hashAlgorithm, String hash, long size,
                             ModpackImporter.ProgressListener listener) throws IOException {
        fetch(Collections.singletonList(url), destination, hashAlgorithm, hash, size, listener);
    }

    /**
     * Downloads a pack's own archive into a temporary file, which the caller owns.
     */
    public static File fetchToTemp(String url, String hashAlgorithm, String hash, long size,
                                   ModpackImporter.ProgressListener listener) throws IOException {
        File temp = Files.createTempFile("legism-pack-", ".zip").toFile();
        try {
            fetch(url, temp, hashAlgorithm, hash, size, listener);
        } catch (IOException e) {
            temp.delete();
            throw e;
        }
        return temp;
    }

    private static void fetchWithRetries(String url, File destination, String hashAlgorithm, String hash,
                                         long size, ModpackImporter.ProgressListener listener) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            checkCancelled(listener);
            try {
                fetchOnce(url, destination, hashAlgorithm, hash, size, listener);
                return;
            } catch (CancelledException | PermanentFailure e) {
                throw e;
            } catch (IOException e) {
                last = e;
                log.warn("Download attempt {} of {} failed for {}: {}", attempt, ATTEMPTS, url, e.toString());
                if (attempt < ATTEMPTS) {
                    pause(1000L * attempt, listener);
                }
            }
        }
        throw last;
    }

    private static void fetchOnce(String url, File destination, String hashAlgorithm, String hash, long size,
                                  ModpackImporter.ProgressListener listener) throws IOException {
        // several downloads create the same folders at once; this one tolerates that, the
        // launcher's own helper reports the loser of the race as a failure
        Files.createDirectories(destination.getParentFile().toPath());
        File partial = new File(destination.getParentFile(), destination.getName() + ".part");

        try {
            EHttpClient.getGlobalClient().execute(request(url), response -> {
                checkStatus(response.getCode(), url);
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("no content received for " + url);
                }
                long announced = entity.getContentLength();
                long total = announced > 0 ? announced : size;

                MessageDigest digest = StringUtils.isEmpty(hash) || hashAlgorithm == null ? null : digest(hashAlgorithm);
                long done = 0;
                if (listener != null) {
                    listener.onBytes(0, total);
                }
                try (InputStream in = entity.getContent();
                     OutputStream out = new FileOutputStream(partial)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        checkCancelled(listener);
                        out.write(buffer, 0, read);
                        if (digest != null) {
                            digest.update(buffer, 0, read);
                        }
                        done += read;
                        if (listener != null) {
                            listener.onBytes(done, total);
                        }
                    }
                }
                if (announced > 0 && done != announced) {
                    throw new IOException("download stopped early: got " + done + " of " + announced + " bytes");
                }
                if (digest != null && !hash.equalsIgnoreCase(hex(digest.digest()))) {
                    throw new IOException(destination.getName() + " does not match its published "
                            + hashAlgorithm + " hash");
                }
                return null;
            });
            Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            partial.delete();
        }
    }

    /**
     * Reads a small API answer as text, retrying a dropped connection or a server error.
     */
    public static String getText(String url) throws IOException {
        return getText(url, null);
    }

    /**
     * @param apiKey sent as CurseForge's {@code x-api-key} header when not {@code null}
     */
    public static String getText(String url, String apiKey) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpGet request = request(url);
                request.addHeader(HttpHeaders.ACCEPT, "application/json");
                if (apiKey != null) {
                    request.addHeader("x-api-key", apiKey);
                }
                return EHttpClient.getGlobalClient().execute(request, response -> {
                    checkStatus(response.getCode(), url);
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        throw new IOException("empty response from " + url);
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (InputStream in = entity.getContent()) {
                        byte[] buffer = new byte[16 * 1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                });
            } catch (PermanentFailure e) {
                throw e;
            } catch (IOException e) {
                last = e;
                log.warn("Request attempt {} of {} failed for {}: {}", attempt, ATTEMPTS, url, e.toString());
                if (attempt < ATTEMPTS) {
                    try {
                        Thread.sleep(700L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", interrupted);
                    }
                }
            }
        }
        throw last;
    }

    private static HttpGet request(String url) {
        HttpGet request = new HttpGet(url);
        request.setConfig(REQUEST_CONFIG);
        request.addHeader(HttpHeaders.USER_AGENT, LauncherUserAgent.USER_AGENT);
        return request;
    }

    private static void checkStatus(int code, String url) throws IOException {
        if (code < 400) {
            return;
        }
        // these will not get better by asking again - except a timeout or a rate limit,
        // which is exactly what asking again a moment later is for
        String message = "the server answered " + code + " for " + url;
        if (code < 500 && code != 408 && code != 429) {
            throw new PermanentFailure(message);
        }
        throw new IOException(message);
    }

    /**
     * A failure that retrying cannot fix, such as a file that is not there.
     */
    public static final class PermanentFailure extends IOException {
        PermanentFailure(String message) {
            super(message);
        }
    }

    static void checkCancelled(ModpackImporter.ProgressListener listener) throws CancelledException {
        if (listener != null && listener.isCancelled()) {
            throw new CancelledException();
        }
    }

    /**
     * Waits before a retry, but notices a cancel within a fraction of a second rather
     * than making the user sit out the whole delay.
     */
    private static void pause(long millis, ModpackImporter.ProgressListener listener) throws CancelledException {
        long until = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < until) {
            checkCancelled(listener);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancelledException();
            }
        }
    }

    static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is required by the Java platform", e);
        }
    }

    static String hex(byte[] hash) {
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                result.append('0');
            }
            result.append(hex);
        }
        return result.toString();
    }
}
