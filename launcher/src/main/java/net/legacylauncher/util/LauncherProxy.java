package net.legacylauncher.util;

import lombok.extern.slf4j.Slf4j;
import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.configuration.Configuration;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How the launcher reaches the network.
 * <p>
 * Two separate things had to be taught about proxies here. Plain sockets already followed
 * the system settings, which is how a proxy that advertises itself and then never answers
 * could stop the launcher reaching anything at all; and the Apache client did not follow
 * them at all, so anyone genuinely behind an HTTP proxy was never going through it. Both
 * now come from one setting the user can see and change.
 */
@Slf4j
public final class LauncherProxy {

    public static final String SETTING_MODE = "connection.proxy.mode";
    public static final String SETTING_TYPE = "connection.proxy.type";
    public static final String SETTING_HOST = "connection.proxy.host";
    public static final String SETTING_PORT = "connection.proxy.port";
    public static final String SETTING_USER = "connection.proxy.username";
    public static final String SETTING_PASSWORD = "connection.proxy.password";

    private LauncherProxy() {
    }

    /**
     * Whatever is in the settings right now, resolved once so a half-typed manual entry
     * cannot change under a request that is already running.
     */
    private static volatile Resolved current = new Resolved(Configuration.ProxyMode.SYSTEM, null, null, null);

    /**
     * The JVM's own selector, kept so "follow the system settings" can be restored.
     * <p>
     * Captured before anything replaces it, because that is the only way back: setting
     * {@code java.net.useSystemProxies} afterwards does nothing at all. The JVM reads that
     * property once, when the default selector is first built, and the selector is built
     * the first time anything opens a connection - long before the settings are read.
     * Turning a proxy off therefore has to replace the selector, not the property.
     */
    private static final ProxySelector SYSTEM_SELECTOR = ProxySelector.getDefault();

    /**
     * The proxy in effect, as the HTTP client needs it.
     */
    private static final class Resolved {
        final Configuration.ProxyMode mode;
        final HttpHost host;
        final String username;
        final String password;

        Resolved(Configuration.ProxyMode mode, HttpHost host, String username, String password) {
            this.mode = mode;
            this.host = host;
            this.username = username;
            this.password = password;
        }
    }

    /**
     * Reads the settings and puts them into effect, for both plain sockets and the HTTP
     * client. Safe to call again whenever the user changes them.
     */
    public static void apply(Configuration settings) {
        Configuration.ProxyMode mode = readMode(settings);
        HttpHost host = null;
        String username = null;
        String password = null;

        switch (mode) {
            case NONE:
                // The point of this setting: a system proxy that is present but broken
                // otherwise leaves every request failing with nothing the user can do
                // about it from inside the launcher.
                clearSocketProperties();
                ProxySelector.setDefault(fixed(Proxy.NO_PROXY));
                installAuthenticator(null, null);
                break;
            case MANUAL:
                String manualHost = trimmed(settings.get(SETTING_HOST));
                int port = readPort(settings);
                boolean socks = isSocks(settings);
                if (manualHost.isEmpty() || port <= 0) {
                    log.warn("Proxy is set to manual but the host or port is missing; going direct");
                    clearSocketProperties();
                    ProxySelector.setDefault(fixed(Proxy.NO_PROXY));
                    installAuthenticator(null, null);
                    break;
                }
                clearSocketProperties();
                applySocketProperties(manualHost, port, socks);
                ProxySelector.setDefault(fixed(new Proxy(
                        socks ? Proxy.Type.SOCKS : Proxy.Type.HTTP,
                        new InetSocketAddress(manualHost, port))));
                username = trimmed(settings.get(SETTING_USER));
                password = settings.get(SETTING_PASSWORD) == null ? "" : settings.get(SETTING_PASSWORD);
                if (!socks) {
                    // SOCKS is handled by the socket layer, so only an HTTP proxy is worth
                    // handing to the HTTP client as a route
                    host = new HttpHost("http", manualHost, port);
                }
                installAuthenticator(username, password);
                break;
            case SYSTEM:
            default:
                clearSocketProperties();
                ProxySelector.setDefault(SYSTEM_SELECTOR);
                installAuthenticator(null, null);
                break;
        }

        current = new Resolved(mode, host, username, password);
        log.info("Proxy mode: {}{}", mode, host == null ? "" : " via " + host);
    }

    /**
     * Points the HTTP client at the configured proxy. Called for every client the launcher
     * builds, so a change of setting reaches anything built afterwards.
     */
    public static void configure(HttpClientBuilder builder) {
        Resolved resolved = current;
        if (resolved.mode == Configuration.ProxyMode.SYSTEM) {
            // follow the JVM's own view of the system settings, which is what the rest of
            // the launcher has always done
            builder.useSystemProperties();
            return;
        }
        if (resolved.host == null) {
            return;
        }
        builder.setRoutePlanner(new DefaultProxyRoutePlanner(resolved.host));
        if (!resolved.username.isEmpty()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(
                    new AuthScope(resolved.host),
                    new UsernamePasswordCredentials(resolved.username, resolved.password.toCharArray()));
            builder.setDefaultCredentialsProvider(credentials);
        }
    }

    private static Configuration.ProxyMode readMode(Configuration settings) {
        try {
            String raw = settings.get(SETTING_MODE);
            if (raw == null || raw.trim().isEmpty()) {
                return Configuration.ProxyMode.SYSTEM;
            }
            return Configuration.ProxyMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown proxy mode {}, falling back to the system settings", settings.get(SETTING_MODE));
            return Configuration.ProxyMode.SYSTEM;
        }
    }

    private static boolean isSocks(Configuration settings) {
        String type = trimmed(settings.get(SETTING_TYPE));
        return type.equalsIgnoreCase("socks");
    }

    private static int readPort(Configuration settings) {
        try {
            String raw = trimmed(settings.get(SETTING_PORT));
            return raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void applySocketProperties(String host, int port, boolean socks) {
        if (socks) {
            System.setProperty("socksProxyHost", host);
            System.setProperty("socksProxyPort", String.valueOf(port));
            System.clearProperty("http.proxyHost");
            System.clearProperty("https.proxyHost");
        } else {
            System.setProperty("http.proxyHost", host);
            System.setProperty("http.proxyPort", String.valueOf(port));
            System.setProperty("https.proxyHost", host);
            System.setProperty("https.proxyPort", String.valueOf(port));
            System.clearProperty("socksProxyHost");
            System.clearProperty("socksProxyPort");
        }
    }

    private static void clearSocketProperties() {
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    /**
     * Answers the proxy's own password prompt. Only ever offers the credentials back to a
     * proxy, never to the site being fetched.
     */
    private static void installAuthenticator(String username, String password) {
        if (username == null || username.isEmpty()) {
            Authenticator.setDefault(null);
            return;
        }
        final char[] secret = password.toCharArray();
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY) {
                    return null;
                }
                return new PasswordAuthentication(username, secret);
            }
        });
    }

    /**
     * A selector that answers the same way for every address, replacing whatever the JVM
     * worked out from the system.
     */
    private static ProxySelector fixed(Proxy proxy) {
        final List<Proxy> answer = Collections.singletonList(proxy);
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return answer;
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
                log.debug("Could not reach {} via {}: {}", uri, address, failure.toString());
            }
        };
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Re-reads the settings from the running launcher. For the settings screen, which has
     * no reason to know how any of this is wired up.
     */
    public static void reapply() {
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        if (launcher != null) {
            apply(launcher.getSettings());
        }
    }
}
