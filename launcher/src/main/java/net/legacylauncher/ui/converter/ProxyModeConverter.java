package net.legacylauncher.ui.converter;

import net.legacylauncher.configuration.Configuration;
import net.legacylauncher.ui.loc.LocalizableStringConverter;

import java.util.Locale;

public class ProxyModeConverter extends LocalizableStringConverter<Configuration.ProxyMode> {
    public ProxyModeConverter() {
        super("settings.proxy.mode");
    }

    public Configuration.ProxyMode fromString(String from) {
        if (from == null || from.trim().isEmpty()) {
            return Configuration.ProxyMode.SYSTEM;
        }
        try {
            return Configuration.ProxyMode.valueOf(from.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Configuration.ProxyMode.SYSTEM;
        }
    }

    public String toValue(Configuration.ProxyMode from) {
        return from == null ? null : from.toString();
    }

    public String toPath(Configuration.ProxyMode from) {
        return from == null ? null : from.toString().toLowerCase(Locale.ROOT);
    }

    public Class<Configuration.ProxyMode> getObjectClass() {
        return Configuration.ProxyMode.class;
    }
}
