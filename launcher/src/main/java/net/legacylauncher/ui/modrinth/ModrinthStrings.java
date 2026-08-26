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

        en("library", "Library");
        ru("library", "Библиотека");

        en("curseforge.no-key", "CurseForge answers nothing without an API key of your own. "
                + "Get one free at console.curseforge.com and paste it into Settings -> Launcher.");
        ru("curseforge.no-key", "CurseForge не отвечает без собственного API-ключа. "
                + "Получите бесплатный на console.curseforge.com и вставьте его в Параметры -> Лаунчер.");

        en("curseforge.apikey", "CurseForge API key");
        ru("curseforge.apikey", "API-ключ CurseForge");

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

        en("open-folder.mod", "Open mods folder");
        ru("open-folder.mod", "Открыть папку модов");

        en("open-folder.resourcepack", "Open resource packs folder");
        ru("open-folder.resourcepack", "Открыть папку ресурспаков");

        en("open-folder.shader", "Open shaders folder");
        ru("open-folder.shader", "Открыть папку шейдеров");

        en("open-folder.datapack", "Open data packs folder");
        ru("open-folder.datapack", "Открыть папку дата-паков");

        en("open-folder.worlds", "Open saves folder");
        ru("open-folder.worlds", "Открыть папку миров");

        en("open-folder.plugin", "Open plugins folder");
        ru("open-folder.plugin", "Открыть папку плагинов");

        en("load-more", "Load more");
        ru("load-more", "Показать ещё");

        en("loading", "Loading...");
        ru("loading", "Загрузка...");

        en("empty", "Nothing found. Try another search, game version or loader.");
        ru("empty", "Ничего не найдено. Попробуйте другой запрос, версию игры или загрузчик.");

        en("empty.installed", "No mods installed for this version yet.");
        ru("empty.installed", "Для этой версии ещё нет установленных модов.");

        en("update", "Update");
        ru("update", "Обновить");

        en("update-available", "Update available");
        ru("update-available", "Доступно обновление");

        en("update-all", "Update all");
        ru("update-all", "Обновить всё");

        en("updating", "Updating...");
        ru("updating", "Обновление...");

        en("update-all.done", "Updated %0 file(s)");
        ru("update-all.done", "Обновлено файлов: %0");

        en("error.update", "Could not update the file.");
        ru("error.update", "Не удалось обновить файл.");

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

        // content types
        en("type.mod", "Mods");
        ru("type.mod", "Моды");

        en("type.resourcepack", "Resource packs");
        ru("type.resourcepack", "Ресурспаки");

        en("type.shader", "Shaders");
        ru("type.shader", "Шейдеры");

        en("type.datapack", "Data packs");
        ru("type.datapack", "Дата-паки");

        en("type.plugin", "Plugins");
        ru("type.plugin", "Плагины");

        // worlds
        en("tab.worlds", "Worlds");
        ru("tab.worlds", "Миры");

        en("worlds.empty", "This instance has no worlds yet. They appear here once you play.");
        ru("worlds.empty", "В этой сборке пока нет миров. Они появятся здесь после игры.");

        en("worlds.import", "Import from .zip");
        ru("worlds.import", "Импорт из .zip");

        en("worlds.import.title", "Choose a world archive");
        ru("worlds.import.title", "Выберите архив с миром");

        en("worlds.imported", "Imported %0");
        ru("worlds.imported", "Импортирован мир %0");

        en("worlds.error.import", "Could not import the world.");
        ru("worlds.error.import", "Не удалось импортировать мир.");

        en("worlds.not-on-modrinth", "Modrinth does not host worlds, so they cannot be "
                + "downloaded here. Import a .zip you already have, or create one in game.");
        ru("worlds.not-on-modrinth", "Modrinth не хранит миры, поэтому скачать их отсюда нельзя. "
                + "Импортируйте готовый .zip или создайте мир в игре.");

        en("worlds.size", "%0 on disk");
        ru("worlds.size", "%0 на диске");

        // instances
        en("instances.title", "Instances");
        ru("instances.title", "Сборки");

        en("instances.create", "Create instance");
        ru("instances.create", "Создать сборку");

        en("instances.edit", "Edit");
        ru("instances.edit", "Изменить");

        en("instances.play", "Play");
        ru("instances.play", "Играть");

        en("instances.delete", "Delete");
        ru("instances.delete", "Удалить");

        en("instances.rename", "Rename");
        ru("instances.rename", "Переименовать");

        en("instances.open-folder", "Open folder");
        ru("instances.open-folder", "Открыть папку");

        en("instances.empty", "No instances yet. Create one to keep its mods, worlds and "
                + "settings separate from everything else.");
        ru("instances.empty", "Сборок пока нет. Создайте первую — её моды, миры и настройки "
                + "не будут смешиваться с остальными.");

        en("instances.new.title", "New instance");
        ru("instances.new.title", "Новая сборка");

        en("instances.new.name", "Name");
        ru("instances.new.name", "Название");

        en("instances.new.version", "Minecraft version");
        ru("instances.new.version", "Версия Minecraft");

        en("instances.new.loader", "Mod loader");
        ru("instances.new.loader", "Загрузчик модов");

        en("instances.new.loader.none", "None (vanilla)");
        ru("instances.new.loader.none", "Без загрузчика (ваниль)");

        en("instances.new.create", "Create");
        ru("instances.new.create", "Создать");

        en("instances.new.cancel", "Cancel");
        ru("instances.new.cancel", "Отмена");

        en("instances.rename.prompt", "New name for \"%0\":");
        ru("instances.rename.prompt", "Новое название для «%0»:");

        en("instances.confirm.delete", "Delete the instance \"%0\" with all its mods and "
                + "worlds? This cannot be undone.");
        ru("instances.confirm.delete", "Удалить сборку «%0» вместе со всеми модами и мирами? "
                + "Это действие необратимо.");

        en("instances.error.create", "Could not create the instance.");
        ru("instances.error.create", "Не удалось создать сборку.");

        en("instances.import", "Import...");
        ru("instances.import", "Импортировать...");

        en("instances.error.import", "Could not import the modpack.");
        ru("instances.error.import", "Не удалось импортировать сборку.");

        en("instances.error.import-format", "Not a recognised modpack: expected a Modrinth "
                + ".mrpack or an exported instance zip.");
        ru("instances.error.import-format", "Формат не распознан: ожидался Modrinth .mrpack "
                + "или zip, экспортированный из этого лаунчера.");

        en("instances.error.delete", "Could not delete the instance.");
        ru("instances.error.delete", "Не удалось удалить сборку.");

        en("instances.never-played", "never played");
        ru("instances.never-played", "ещё не запускалась");

        en("instances.last-played", "last played %0");
        ru("instances.last-played", "запуск: %0");

        en("instances.versions-loading", "Loading the version list...");
        ru("instances.versions-loading", "Загрузка списка версий...");

        en("edit.title", "Instance: %0");
        ru("edit.title", "Сборка: %0");

        // instance screen: toolbar
        en("instances.folders", "Folders");
        ru("instances.folders", "Папки");

        en("instances.folder.instances", "Instances folder");
        ru("instances.folder.instances", "Папка сборок");

        en("instances.folder.game", "Game folder");
        ru("instances.folder.game", "Папка игры");

        en("instances.settings", "Settings");
        ru("instances.settings", "Параметры");

        en("instances.help", "Help");
        ru("instances.help", "Справка");

        en("instances.help.modrinth", "Open Modrinth");
        ru("instances.help.modrinth", "Открыть Modrinth");

        en("instances.help.about", "About");
        ru("instances.help.about", "О программе");

        en("instances.accounts", "Accounts");
        ru("instances.accounts", "Аккаунты");

        en("instances.update-available", "Update");
        ru("instances.update-available", "Обновление");

        en("instances.accounts.clear", "Clear the active account");
        ru("instances.accounts.clear", "Убрать аккаунт по умолчанию");

        en("instances.accounts.manage", "Manage accounts...");
        ru("instances.accounts.manage", "Управление учётными записями...");

        // instance screen: sidebar and groups
        en("instances.stop", "Stop");
        ru("instances.stop", "Остановить");

        en("instances.change-icon", "Change icon...");
        ru("instances.change-icon", "Изменить значок...");

        en("instances.change-icon.upload", "Upload image...");
        ru("instances.change-icon.upload", "Загрузить изображение...");

        en("instances.group", "Change group...");
        ru("instances.group", "Изменить группу...");

        en("instances.group.prompt", "Group for \"%0\" (empty for none):");
        ru("instances.group.prompt", "Группа для «%0» (пусто — без группы):");

        en("instances.export", "Export...");
        ru("instances.export", "Экспортировать...");

        en("instances.exported", "Exported to %0");
        ru("instances.exported", "Экспортировано в %0");

        en("instances.duplicate", "Copy...");
        ru("instances.duplicate", "Копировать...");

        en("instances.duplicate.prompt", "Name for the copy of \"%0\":");
        ru("instances.duplicate.prompt", "Название копии «%0»:");

        en("instances.shortcut", "Create shortcut");
        ru("instances.shortcut", "Создать ярлык");

        en("instances.shortcut-created", "Shortcut written to %0");
        ru("instances.shortcut-created", "Ярлык создан: %0");

        en("instances.default-group", "Ungrouped");
        ru("instances.default-group", "Без группы");

        en("instances.select-hint", "Select an instance");
        ru("instances.select-hint", "Выберите сборку");

        en("instances.total-playtime", "Total playtime: %0");
        ru("instances.total-playtime", "Всего наиграно: %0");

        en("instances.duration.hm", "%0h %1min");
        ru("instances.duration.hm", "%0ч %1мин");

        en("instances.duration.m", "%0min");
        ru("instances.duration.m", "%0мин");

        en("instances.error.duplicate", "Could not copy the instance.");
        ru("instances.error.duplicate", "Не удалось скопировать сборку.");

        en("instances.error.export", "Could not export the instance.");
        ru("instances.error.export", "Не удалось экспортировать сборку.");

        en("instances.error.shortcut", "Could not create the shortcut.");
        ru("instances.error.shortcut", "Не удалось создать ярлык.");

        en("instances.error.shortcut-target", "Could not work out which executable to point "
                + "the shortcut at. This works in the installed or portable build.");
        ru("instances.error.shortcut-target", "Не удалось определить, на какой исполняемый файл "
                + "сослаться. Это работает в установленной или портативной сборке.");

        en("tab.log", "Log"); ru("tab.log", "Журнал");
        en("tab.version", "Version"); ru("tab.version", "Версия");
        en("tab.notes", "Notes"); ru("tab.notes", "Заметки");
        en("tab.servers", "Servers"); ru("tab.servers", "Серверы");
        en("tab.screenshots", "Screenshots"); ru("tab.screenshots", "Скриншоты");
        en("tab.instance-settings", "Settings"); ru("tab.instance-settings", "Параметры");
        en("tab.other-logs", "Other logs"); ru("tab.other-logs", "Другие журналы");

        en("instance.settings.memory", "Memory");
        ru("instance.settings.memory", "Память (ОЗУ)");
        en("instance.settings.folder", "Game folder");
        ru("instance.settings.folder", "Папка игры");
        en("instance.settings.save", "Save"); ru("instance.settings.save", "Сохранить");
        en("instance.settings.saved", "Saved"); ru("instance.settings.saved", "Сохранено");
        en("instance.settings.invalid", "Check the memory value before saving.");
        ru("instance.settings.invalid", "Проверьте значение памяти перед сохранением.");

        en("instance.notes.save", "Save"); ru("instance.notes.save", "Сохранить");
        en("instance.notes.saved", "Saved"); ru("instance.notes.saved", "Сохранено");

        en("instance.servers.empty", "No servers added yet.");
        ru("instance.servers.empty", "Серверов пока нет.");
        en("instance.servers.add", "Add server"); ru("instance.servers.add", "Добавить сервер");
        en("instance.servers.remove", "Remove"); ru("instance.servers.remove", "Удалить");
        en("instance.servers.up", "Move up"); ru("instance.servers.up", "Переместить вверх");
        en("instance.servers.down", "Move down"); ru("instance.servers.down", "Переместить вниз");
        en("instance.servers.name-prompt", "Server name:");
        ru("instance.servers.name-prompt", "Название сервера:");
        en("instance.servers.address-prompt", "Server address (host:port):");
        ru("instance.servers.address-prompt", "Адрес сервера (хост:порт):");
        en("instance.servers.pinging", "Pinging...");
        ru("instance.servers.pinging", "Пингуем...");
        en("instance.servers.offline", "Offline or not responding");
        ru("instance.servers.offline", "Не отвечает");
        en("instance.servers.players", "players");
        ru("instance.servers.players", "игроков");

        en("instance.screenshots.empty", "No screenshots yet.");
        ru("instance.screenshots.empty", "Скриншотов пока нет.");
        en("instance.screenshots.open", "Open"); ru("instance.screenshots.open", "Открыть");
        en("instance.screenshots.confirm-delete", "Delete %0?");
        ru("instance.screenshots.confirm-delete", "Удалить %0?");

        en("instance.version.minecraft", "Minecraft version");
        ru("instance.version.minecraft", "Версия Minecraft");
        en("instance.version.loader", "Mod loader"); ru("instance.version.loader", "Загрузчик модов");
        en("instance.version.id", "Launcher version id"); ru("instance.version.id", "ID версии в лаунчере");
        en("instance.version.created", "Created"); ru("instance.version.created", "Создана");
        en("instance.version.last-played", "Last played"); ru("instance.version.last-played", "Последний запуск");
        en("instance.version.folder", "Instance folder"); ru("instance.version.folder", "Папка сборки");

        en("instance.log.none", "No log yet - nothing has run in this instance.");
        ru("instance.log.none", "Журнала пока нет — в этой сборке ещё ничего не запускалось.");

        en("instance.other-logs.empty", "No other logs or crash reports yet.");
        ru("instance.other-logs.empty", "Других журналов и крэш-репортов пока нет.");

        // server hosting
        en("server.title", "Local Servers");
        ru("server.title", "Локальные серверы");

        en("server.empty", "No local servers yet. Create one to host your own Minecraft server.");
        ru("server.empty", "Локальных серверов пока нет. Создайте сервер, чтобы захостить свой мир.");

        en("server.create", "Create server");
        ru("server.create", "Создать сервер");

        en("server.delete", "Delete");
        ru("server.delete", "Удалить");

        en("server.delete.confirm", "Delete server %0? This removes its whole folder, worlds included.");
        ru("server.delete.confirm", "Удалить сервер %0? Будет удалена вся его папка, включая миры.");

        en("server.error.create", "Could not create the server");
        ru("server.error.create", "Не удалось создать сервер");

        en("server.error.delete", "Could not delete the server");
        ru("server.error.delete", "Не удалось удалить сервер");

        en("server.new.title", "New local server");
        ru("server.new.title", "Новый локальный сервер");
        en("server.new.name", "Name");
        ru("server.new.name", "Название");
        en("server.new.core", "Core");
        ru("server.new.core", "Ядро");
        en("server.new.version", "Minecraft version");
        ru("server.new.version", "Версия Minecraft");
        en("server.new.ram", "RAM (MB)");
        ru("server.new.ram", "ОЗУ (МБ)");
        en("server.new.versions-loading", "Loading...");
        ru("server.new.versions-loading", "Загрузка...");
        en("server.new.cancel", "Cancel");
        ru("server.new.cancel", "Отмена");
        en("server.new.create", "Create");
        ru("server.new.create", "Создать");

        en("server.tab.console", "Console");
        ru("server.tab.console", "Консоль");
        en("server.tab.properties", "Properties");
        ru("server.tab.properties", "Свойства");
        en("server.tab.plugins", "Plugins");
        ru("server.tab.plugins", "Плагины");

        en("server.console.start", "Start");
        ru("server.console.start", "Запустить");
        en("server.console.stop", "Stop");
        ru("server.console.stop", "Остановить");
        en("server.console.command-hint", "Type a command and press Enter");
        ru("server.console.command-hint", "Введите команду и нажмите Enter");
        en("server.console.running", "Running");
        ru("server.console.running", "Запущен");
        en("server.console.stopped", "Stopped");
        ru("server.console.stopped", "Остановлен");
        en("server.console.starting", "Starting...");
        ru("server.console.starting", "Запускается...");
        en("server.console.downloading", "Downloading %0...");
        ru("server.console.downloading", "Загрузка %0...");
        en("server.console.download-failed", "Could not download the server core");
        ru("server.console.download-failed", "Не удалось загрузить ядро сервера");
        en("server.console.start-failed", "Could not start the server");
        ru("server.console.start-failed", "Не удалось запустить сервер");

        en("server.eula.title", "Accept the Minecraft EULA");
        ru("server.eula.title", "Примите EULA Minecraft");
        en("server.eula.message", "Running a Minecraft server requires accepting Mojang's EULA "
                + "(https://aka.ms/MinecraftEULA). Accept it and start the server?");
        ru("server.eula.message", "Для запуска сервера Minecraft нужно принять EULA от Mojang "
                + "(https://aka.ms/MinecraftEULA). Принять и запустить сервер?");

        en("server.properties.port", "Port");
        ru("server.properties.port", "Порт");
        en("server.properties.motd", "MOTD");
        ru("server.properties.motd", "MOTD");
        en("server.properties.max-players", "Max players");
        ru("server.properties.max-players", "Макс. игроков");
        en("server.properties.difficulty", "Difficulty");
        ru("server.properties.difficulty", "Сложность");
        en("server.properties.gamemode", "Game mode");
        ru("server.properties.gamemode", "Режим игры");
        en("server.properties.online-mode", "Online mode (license check)");
        ru("server.properties.online-mode", "Онлайн-режим (проверка лицензии)");
        en("server.properties.pvp", "PvP");
        ru("server.properties.pvp", "PvP");
        en("server.properties.white-list", "Whitelist");
        ru("server.properties.white-list", "Белый список");
        en("server.properties.save", "Save");
        ru("server.properties.save", "Сохранить");
        en("server.properties.saved", "Saved. Restart the server to apply.");
        ru("server.properties.saved", "Сохранено. Перезапустите сервер, чтобы применить.");

        en("server.plugins.vanilla-hint", "Vanilla servers cannot load plugins - switch to Paper "
                + "or Purpur when creating the server to use this tab.");
        ru("server.plugins.vanilla-hint", "Vanilla-сервер не умеет загружать плагины — выберите "
                + "Paper или Purpur при создании сервера, чтобы пользоваться этой вкладкой.");
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
