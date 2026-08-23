package net.legacylauncher.ui.modrinth;

import net.legacylauncher.LegacyLauncher;
import net.legacylauncher.configuration.Configuration;
import net.legacylauncher.ui.loc.Localizable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Strings for the Modrinth screens.
 * <p>
 * The launcher's translations live in a git submodule that is not part of every source
 * checkout, and this fork adds keys that no upstream translation file knows about. So the
 * English and Russian texts are carried here: a translation is used when the lang files
 * happen to define {@code modrinth.<key>}, and the built-in text is used otherwise.
 */
public final class ModrinthStrings {
    private static final String PREFIX = "modrinth.";

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> RU = new HashMap<>();

    static {
        en("title", "Mods");
        ru("title", "Моды");

        en("back", "Back");
        ru("back", "Назад");

        en("search.hint", "Search Modrinth");
        ru("search.hint", "Поиск по Modrinth");

        en("search", "Search");
        ru("search", "Искать");

        en("tab.browse", "Browse");
        ru("tab.browse", "Каталог");

        en("tab.installed", "Installed");
        ru("tab.installed", "Установленные");

        en("game-version", "Minecraft");
        ru("game-version", "Minecraft");

        en("loader", "Loader");
        ru("loader", "Загрузчик");

        en("sort", "Sort by");
        ru("sort", "Сортировка");

        en("sort.relevance", "Relevance");
        ru("sort.relevance", "Соответствию");

        en("sort.downloads", "Downloads");
        ru("sort.downloads", "Загрузкам");

        en("sort.follows", "Followers");
        ru("sort.follows", "Подписчикам");

        en("sort.newest", "Newest");
        ru("sort.newest", "Дате создания");

        en("sort.updated", "Recently updated");
        ru("sort.updated", "Дате обновления");

        en("dependencies", "Install required dependencies");
        ru("dependencies", "Ставить обязательные зависимости");

        en("install", "Install");
        ru("install", "Установить");

        en("installed", "Installed");
        ru("installed", "Установлен");

        en("installing", "Installing");
        ru("installing", "Установка");

        en("open", "Open on Modrinth");
        ru("open", "Открыть на Modrinth");

        en("delete", "Delete");
        ru("delete", "Удалить");

        en("enable", "Enable");
        ru("enable", "Включить");

        en("disable", "Disable");
        ru("disable", "Выключить");

        en("refresh", "Refresh");
        ru("refresh", "Обновить");

        en("open-folder", "Open mods folder");
        ru("open-folder", "Открыть папку модов");

        en("load-more", "Load more");
        ru("load-more", "Показать ещё");

        en("loading", "Loading...");
        ru("loading", "Загрузка...");

        en("empty", "Nothing found. Try another search, game version or loader.");
        ru("empty", "Ничего не найдено. Попробуйте другой запрос, версию игры или загрузчик.");

        en("empty.installed", "No mods installed for this version yet.");
        ru("empty.installed", "Для этой версии ещё нет установленных модов.");

        en("no-version-selected", "Select an installed Minecraft version first.");
        ru("no-version-selected", "Сначала выберите установленную версию Minecraft.");

        en("vanilla", "This is a vanilla version: it cannot load mods. Install a Forge, "
                + "NeoForge, Fabric or Quilt version, or pick a loader above to browse anyway.");
        ru("vanilla", "Это ванильная версия: она не загружает моды. Установите версию с Forge, "
                + "NeoForge, Fabric или Quilt — либо выберите загрузчик выше, чтобы просто "
                + "посмотреть каталог.");

        en("no-compatible-version", "This mod has no build for %0 / %1.");
        ru("no-compatible-version", "У этого мода нет сборки для %0 / %1.");

        en("installed-into", "Installed %0 file(s) into %1");
        ru("installed-into", "Установлено файлов: %0. Папка: %1");

        en("error.title", "Modrinth");
        ru("error.title", "Modrinth");

        en("error.search", "Could not search Modrinth.");
        ru("error.search", "Не удалось выполнить поиск на Modrinth.");

        en("error.install", "Could not install the mod.");
        ru("error.install", "Не удалось установить мод.");

        en("error.delete", "Could not delete the file.");
        ru("error.delete", "Не удалось удалить файл.");

        en("confirm.delete", "Delete %0?");
        ru("confirm.delete", "Удалить %0?");

        en("target", "Mods for %0");
        ru("target", "Моды для %0");
    }

    private ModrinthStrings() {
    }

    private static void en(String key, String value) {
        EN.put(key, value);
    }

    private static void ru(String key, String value) {
        RU.put(key, value);
    }

    /**
     * @param key key without the {@code modrinth.} prefix
     */
    public static String get(String key) {
        String translated = Localizable.nget(PREFIX + key);
        if (translated != null && !translated.isEmpty()) {
            return translated;
        }
        String builtin = strings().get(key);
        return builtin == null ? PREFIX + key : builtin;
    }

    /**
     * Same as {@link #get(String)}, replacing {@code %0}, {@code %1}, ... with the given
     * values.
     */
    public static String get(String key, Object... vars) {
        String value = get(key);
        for (int i = 0; i < vars.length; i++) {
            value = value.replace("%" + i, String.valueOf(vars[i]));
        }
        return value;
    }

    private static Map<String, String> strings() {
        Locale locale = currentLocale();
        if (locale != null && Configuration.isLikelyRussianSpeakingLocale(locale.toString())) {
            return RU;
        }
        return EN;
    }

    private static Locale currentLocale() {
        if (Localizable.exists()) {
            return Localizable.get().getLocale();
        }
        LegacyLauncher launcher = LegacyLauncher.getInstance();
        return launcher == null ? null : launcher.getSettings().getLocale();
    }
}
