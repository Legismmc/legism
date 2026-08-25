package net.legacylauncher.ui.converter;

import net.legacylauncher.configuration.Configuration;
import net.legacylauncher.ui.loc.LocalizableStringConverter;

public class UiThemeConverter extends LocalizableStringConverter<Configuration.UiTheme> {
    public UiThemeConverter() {
        super("settings.uitheme");
    }

    public Configuration.UiTheme fromString(String from) {
        return Configuration.UiTheme.get(from);
    }

    public String toValue(Configuration.UiTheme from) {
        return from == null ? null : from.toString();
    }

    public String toPath(Configuration.UiTheme from) {
        return from == null ? null : from.toString();
    }

    public Class<Configuration.UiTheme> getObjectClass() {
        return Configuration.UiTheme.class;
    }
}
