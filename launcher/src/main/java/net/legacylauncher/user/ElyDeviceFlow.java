package net.legacylauncher.user;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.util.ua.LauncherUserAgent;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Signing in to Ely.by the way a launcher is meant to: the user is shown a short code,
 * types it on Ely.by's own page, and this waits for that to happen.
 * <p>
 * The redirect-based flow the launcher used before does not work for this application at
 * all. That one was registered as a confidential client - it had a secret to prove itself
 * with. This fork's own registration is a public client, as any desktop app has to be, and
 * for those Ely.by answers the authorisation page with "invalid_request", naming no
 * parameter, whatever is sent. The device flow is what public clients are given instead.
 */
@Slf4j
public final class ElyDeviceFlow {

    private static final String DEVICE_CODE_URL = ElyAuth.API_BASE + "/oauth2/v1/devicecode";
    private static final String TOKEN_URL = ElyAuth.API_BASE + "/oauth2/v1/token";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code";

    private static final String SCOPES = "account_info minecraft_server_session";

    /**
     * Used when Ely.by does not say how often to poll. Its own answer is five seconds.
     */
    private static final int DEFAULT_INTERVAL_SECONDS = 5;

    private final Gson gson = new GsonBuilder().create();

    /**
     * Told the code as soon as there is one, so it can be put on screen while the wait
     * begins.
     */
    public interface Listener {
        void onCodeIssued(String userCode, String verificationUri);
    }

    /**
     * Runs the whole exchange, blocking until the user finishes or it fails.
     *
     * @throws AuthException        if Ely.by refuses, the user declines, or the code expires
     * @throws InterruptedException if the sign-in is cancelled
     */
    public ElyUser authorize(Listener listener) throws IOException, AuthException, InterruptedException {
        DeviceCode device = requestDeviceCode();
        log.info("Ely.by device code issued, user enters {} at {}", device.user_code, device.verification_uri);
        listener.onCodeIssued(device.user_code, device.verification_uri);

        int interval = device.interval > 0 ? device.interval : DEFAULT_INTERVAL_SECONDS;
        long deadline = System.currentTimeMillis()
                + (device.expires_in > 0 ? device.expires_in : 600) * 1000L;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000L);

            TokenResponse token = pollToken(device.device_code);
            if (token.access_token != null && !token.access_token.isEmpty()) {
                log.info("Ely.by authorised the device");
                return ElyAuthCode.userFromToken(token.access_token, token.refresh_token, token.expires_in);
            }
            if ("authorization_pending".equals(token.error)) {
                continue;
            }
            if ("slow_down".equals(token.error)) {
                // Ely.by asking to back off; the spec says add five seconds and carry on
                interval += 5;
                continue;
            }
            throw new AuthException(token.error == null ? "unknown error" : token.error, token.error);
        }
        throw new AuthException("the code expired before it was entered", "expired_token");
    }

    private DeviceCode requestDeviceCode() throws IOException, AuthException {
        String body = "client_id=" + ElyAuth.CLIENT_ID
                + "&scope=" + SCOPES.replace(" ", "%20");
        DeviceCode code = post(DEVICE_CODE_URL, body, DeviceCode.class);
        if (code == null || code.device_code == null || code.user_code == null) {
            throw new AuthException("Ely.by did not issue a device code", "device_code");
        }
        return code;
    }

    private TokenResponse pollToken(String deviceCode) throws IOException {
        String body = "grant_type=" + GRANT_TYPE
                + "&client_id=" + ElyAuth.CLIENT_ID
                + "&device_code=" + deviceCode;
        try {
            return post(TOKEN_URL, body, TokenResponse.class);
        } catch (AuthException e) {
            // while waiting, "errors" are the normal answer, not a reason to stop
            TokenResponse pending = new TokenResponse();
            pending.error = e.getLocPath();
            return pending;
        }
    }

    private <T> T post(String url, String body, Class<T> type) throws IOException, AuthException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", LauncherUserAgent.USER_AGENT);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        IOUtils.write(body, connection.getOutputStream(), StandardCharsets.UTF_8);

        byte[] read;
        try (InputStream input = connection.getInputStream()) {
            read = IOUtils.toByteArray(input);
        } catch (IOException e) {
            InputStream error = connection.getErrorStream();
            if (error == null) {
                throw e;
            }
            try (InputStream in = error) {
                read = IOUtils.toByteArray(in);
            }
            ErrorResponse parsed = parse(read, ErrorResponse.class);
            throw new AuthException(parsed == null || parsed.message == null ? "request failed" : parsed.message,
                    parsed == null || parsed.error == null ? "unknown" : parsed.error);
        } finally {
            connection.disconnect();
        }
        return parse(read, type);
    }

    private <T> T parse(byte[] data, Class<T> type) {
        return gson.fromJson(new InputStreamReader(
                new java.io.ByteArrayInputStream(data), StandardCharsets.UTF_8), type);
    }

    private static final class DeviceCode {
        String device_code;
        String user_code;
        String verification_uri;
        int expires_in;
        int interval;
    }

    private static final class TokenResponse {
        String access_token;
        String refresh_token;
        int expires_in;
        String error;
    }

    private static final class ErrorResponse {
        String error;
        String message;
    }
}
