/*
 * Language switching for the site.
 *
 * One script shared by every page: each translatable bit of markup carries a data-i18n
 * key, and the dictionary below holds both languages. The choice is kept per visitor in
 * localStorage, so it survives moving between pages and coming back later.
 *
 * The flags are drawn as SVG rather than written as emoji. Windows ships no glyphs for
 * regional-indicator pairs, so an emoji flag renders there as the two letters "GB" or
 * "RU" - which is most of the audience seeing the one thing a flag is meant to avoid.
 */
(function () {
  'use strict';

  var STORAGE_KEY = 'legism.lang';

  var FLAGS = {
    en: '<svg viewBox="0 0 60 30" aria-hidden="true">'
      + '<clipPath id="uk-c"><path d="M0 0v30h60V0z"/></clipPath>'
      + '<g clip-path="url(#uk-c)">'
      + '<path fill="#012169" d="M0 0v30h60V0z"/>'
      + '<path stroke="#fff" stroke-width="6" d="m0 0 60 30m0-30L0 30"/>'
      + '<path stroke="#C8102E" stroke-width="4" d="m0 0 60 30m0-30L0 30"/>'
      + '<path fill="#fff" d="M25 0h10v30H25zM0 10h60v10H0z"/>'
      + '<path fill="#C8102E" d="M27 0h6v30h-6zM0 12h60v6H0z"/>'
      + '</g></svg>',
    ru: '<svg viewBox="0 0 60 30" aria-hidden="true">'
      + '<path fill="#fff" d="M0 0h60v10H0z"/>'
      + '<path fill="#0039A6" d="M0 10h60v10H0z"/>'
      + '<path fill="#D52B1E" d="M0 20h60v10H0z"/>'
      + '</svg>'
  };

  var LABELS = { en: 'English', ru: 'Русский' };

  /**
   * Everything on the site that is not a release note. Notes come from GitHub as their
   * author wrote them and are shown that way.
   */
  var STRINGS = {
    'nav.download': { en: 'Download', ru: 'Скачать' },
    'nav.changelog': { en: 'Changelog', ru: 'Изменения' },

    'hero.formerly': { en: '(formerly Legacy by tgsko)', ru: '(ранее Legacy by tgsko)' },
    'hero.tagline': {
      en: 'A Legacy Launcher fork with no ads and no telemetry — instances, a Modrinth and CurseForge catalog, and your own server.',
      ru: 'Форк Legacy Launcher без рекламы и телеметрии — со сборками, каталогом Modrinth и CurseForge и своим сервером.'
    },
    'hero.download': { en: 'Download', ru: 'Скачать' },
    'hero.platforms': { en: 'Windows, Linux, macOS', ru: 'Windows, Linux, macOS' },
    'hero.allversions': { en: 'all versions', ru: 'все версии' },

    'stats.libraries': { en: 'content libraries', ru: 'библиотеки контента' },
    'stats.types': { en: 'content types', ru: 'типов контента' },
    'stats.cores': { en: 'server cores', ru: 'ядра сервера' },
    'stats.themes': { en: 'themes', ru: 'тем оформления' },

    'why.title': { en: 'Why this fork?', ru: 'Зачем этот форк?' },
    'why.sub': {
      en: "Legism grew out of Legacy Launcher. Here's what actually changed compared to the original.",
      ru: 'Legism вырос из Legacy Launcher. Вот что в нём изменилось по сравнению с оригиналом.'
    },
    'why.col.legism': { en: 'Legism', ru: 'Legism' },
    'why.col.upstream': { en: 'Legacy Launcher', ru: 'Legacy Launcher' },
    'why.ads': { en: 'Ad banners and promo blocks', ru: 'Рекламные баннеры и промо-блоки' },
    'why.ads.a': { en: 'Removed', ru: 'Вырезаны' },
    'why.ads.b': { en: 'Present', ru: 'Есть' },
    'why.servers': { en: 'Servers injected into the in-game list', ru: 'Серверы, добавляемые в список игры' },
    'why.servers.a': { en: 'None, old ones get cleared', ru: 'Нет, старые вычищаются' },
    'why.servers.b': { en: 'Injected', ru: 'Добавляются' },
    'why.telemetry': { en: 'Telemetry, incl. a ping every 30 minutes', ru: 'Телеметрия и «маячок» раз в 30 минут' },
    'why.telemetry.a': { en: 'Disabled', ru: 'Отключены' },
    'why.telemetry.b': { en: 'Present', ru: 'Есть' },
    'why.catalog': { en: 'Modrinth and CurseForge mod catalog', ru: 'Каталог модов Modrinth и CurseForge' },
    'why.catalog.a': { en: 'Built in', ru: 'Встроен' },
    'why.none': { en: 'None', ru: 'Нет' },
    'why.modpacks': { en: 'Modpacks: catalog and file import', ru: 'Модпаки: каталог и импорт файлом' },
    'why.yes': { en: 'Yes', ru: 'Есть' },
    'why.server': { en: 'Local server with a console', ru: 'Локальный сервер с консолью' },
    'why.themes': { en: 'Themes', ru: 'Темы оформления' },
    'why.themes.b': { en: 'Basic', ru: 'Базовые' },
    'why.open': { en: 'Open source', ru: 'Открытый исходный код' },
    'why.open.yes': { en: 'Yes', ru: 'Да' },
    'why.hint': {
      en: "The table scrolls sideways. Rows only describe what actually changed in the fork's own code.",
      ru: 'Таблицу можно прокручивать вбок. Строки описывают только то, что действительно менялось в коде форка.'
    },

    'feat.title': { en: 'Features', ru: 'Возможности' },
    'feat.sub': {
      en: 'Everything lives inside the launcher — no third-party tools, no copying files by hand.',
      ru: 'Всё внутри лаунчера — без сторонних утилит и ручного копирования файлов.'
    },
    'feat.instances': { en: 'Instances', ru: 'Сборки' },
    'feat.instances.d': {
      en: 'Separate installs with their own mods, worlds and settings. Custom icons, groups, cloning and one-file export.',
      ru: 'Отдельные установки со своими модами, мирами и настройками. Свои иконки, группы, копирование и экспорт одним файлом.'
    },
    'feat.catalog': { en: 'Content catalog', ru: 'Каталог контента' },
    'feat.catalog.d': {
      en: 'Mods, resource packs, shaders, data packs and addons from Modrinth and CurseForge — search and install straight into the instance you need.',
      ru: 'Моды, ресурспаки, шейдеры, датапаки и аддоны с Modrinth и CurseForge — поиск и установка прямо в нужную сборку.'
    },
    'feat.modpacks': { en: 'Modpacks', ru: 'Модпаки' },
    'feat.modpacks.d': {
      en: 'Install a whole pack from the catalog in one click. A downloaded .mrpack or CurseForge zip imports from file too.',
      ru: 'Готовые сборки ставятся из каталога одной кнопкой. Скачанный .mrpack или zip с CurseForge импортируется из файла.'
    },
    'feat.server': { en: 'Local server', ru: 'Локальный сервер' },
    'feat.server.d': {
      en: 'The core downloads itself, and the server runs with a live console, a server.properties editor, and its own plugin catalog.',
      ru: 'Ядро скачивается само, сервер запускается с живой консолью, редактором server.properties и каталогом плагинов.'
    },
    'feat.servers': { en: 'Server list', ru: 'Список серверов' },
    'feat.servers.d': {
      en: 'Ping, player count and MOTD, right inside the launcher — without opening the game.',
      ru: 'Пинг, онлайн игроков и MOTD видны прямо в лаунчере — не запуская игру.'
    },
    'feat.accounts': { en: 'Accounts and themes', ru: 'Аккаунты и темы' },
    'feat.accounts.d': {
      en: 'Multiple accounts with Ctrl+1…9 switching, and 17 themes that apply instantly without a restart.',
      ru: 'Несколько учётных записей с переключением по Ctrl+1…9 и 17 тем оформления, применяемых без перезапуска.'
    },

    'dl.title': { en: 'Download', ru: 'Загрузка' },
    'dl.sub': {
      en: 'Windows, Linux and macOS. Java is bundled with every build, so there is nothing else to install.',
      ru: 'Windows, Linux и macOS. Java идёт в комплекте с каждой сборкой — ставить больше ничего не нужно.'
    },
    'dl.win.arch': { en: '10 and 11 · x64 and ARM64', ru: '10 и 11 · x64 и ARM64' },
    'dl.win.files': { en: 'Installer or portable archive', ru: 'Установщик или портативный архив' },
    'dl.linux.arch': { en: 'x86_64 and aarch64 in one archive', ru: 'x86_64 и aarch64 в одном архиве' },
    'dl.linux.files': { en: 'Portable .tar.gz', ru: 'Портативный .tar.gz' },
    'dl.mac.arch': { en: 'Apple Silicon and Intel', ru: 'Apple Silicon и Intel' },
    'dl.mac.files': { en: 'Disk image .dmg', ru: 'Образ диска .dmg' },
    'dl.cta': { en: 'Go to downloads', ru: 'Перейти к загрузкам' },

    'people.title': { en: 'Maintainers', ru: 'Кто делает' },
    'people.sub': { en: 'The people behind the fork.', ru: 'Люди, которые ведут проект.' },
    'people.developer': { en: 'developer', ru: 'разработчик' },
    'people.java': { en: 'java coder', ru: 'java-программист' },

    'faq.title': { en: 'Frequently asked questions', ru: 'Частые вопросы' },
    'faq.sub': { en: 'The short version.', ru: 'Коротко о главном.' },
    'faq.q1': { en: 'What is Legism?', ru: 'Что такое Legism?' },
    'faq.q2': { en: 'Do I need a Minecraft license?', ru: 'Нужна ли лицензия Minecraft?' },
    'faq.q3': { en: 'Is it safe?', ru: 'Это безопасно?' },
    'faq.q4': {
      en: "What's the difference between the installer and the portable version?",
      ru: 'Чем установщик отличается от портативной версии?'
    },
    'faq.q5': { en: 'Which systems does it run on?', ru: 'На каких системах работает?' },
    'faq.q6': { en: 'Do I need my own CurseForge key?', ru: 'Нужен ли свой ключ CurseForge?' },
    'faq.q7': { en: 'Where do I report bugs?', ru: 'Куда сообщать об ошибках?' },

    'community.title': { en: 'Community & Support', ru: 'Сообщество и поддержка' },

    'foot.disclaimer': {
      en: 'Legism is an independent hobby project. It is not affiliated with or endorsed by Mojang Studios, Microsoft, the Legacy Launcher team, CurseForge or Modrinth. Minecraft is a trademark of Mojang Studios. All other names and trademarks belong to their respective owners.',
      ru: 'Legism — независимый любительский проект. Он не связан и не сотрудничает с Mojang Studios, Microsoft, командой Legacy Launcher, CurseForge и Modrinth. Minecraft — торговая марка Mojang Studios. Все названия и торговые марки принадлежат их владельцам.'
    },

    /* download page */
    'dlp.title': { en: 'Download Legism', ru: 'Скачать Legism' },
    'dlp.searching': {
      en: 'Looking for the build that matches your system…',
      ru: 'Подбираем сборку под вашу систему…'
    },
    'dlp.compatible': {
      en: 'This release should be compatible with your OS:',
      ru: 'Эта версия должна подойти вашей системе:'
    },
    'dlp.unknown': {
      en: 'Could not tell which build fits your system. Pick one below.',
      ru: 'Не удалось определить вашу систему. Выберите сборку ниже.'
    },
    'dlp.mobile': {
      en: 'Legism is a desktop launcher — there is no build for phones or tablets.',
      ru: 'Legism — программа для компьютера, сборок для телефонов и планшетов нет.'
    },
    'dlp.other': { en: 'Other downloads:', ru: 'Остальные загрузки:' },
    'dlp.loading': { en: 'Loading…', ru: 'Загрузка…' },
    'dlp.nobuild': { en: 'No build', ru: 'Нет сборки' },
    'dlp.fetched': { en: 'Fetched from the GitHub API', ru: 'Данные из GitHub API' },
    'dlp.unreachable': {
      en: 'GitHub was unreachable — showing the last known release',
      ru: 'GitHub недоступен — показан последний известный релиз'
    },
    'dlp.note': {
      en: 'Java is bundled with every build, so there is nothing else to install. On Linux, unpack the archive and run ./legism.sh. The macOS builds are not signed by Apple, so the first launch needs Control-click → Open rather than a double click.',
      ru: 'Java идёт в комплекте с каждой сборкой — ставить больше ничего не нужно. В Linux распакуйте архив и запустите ./legism.sh. Сборки для macOS не подписаны Apple, поэтому первый запуск делается через Control-клик → «Открыть», а не двойным щелчком.'
    },
    'dlp.downloads': { en: 'downloads across', ru: 'загрузок за' },
    'dlp.releases': { en: 'releases', ru: 'релизов' },

    /* changelog page */
    'cl.title': { en: 'Changelog', ru: 'История изменений' },
    'cl.sub': {
      en: 'Every release, newest first, straight from GitHub.',
      ru: 'Все версии, новые сверху, прямо из GitHub.'
    },
    'cl.loading': { en: 'Loading releases…', ru: 'Загружаем версии…' },
    'cl.failed': {
      en: 'Could not reach GitHub. The releases are listed on the releases page.',
      ru: 'Не удалось связаться с GitHub. Список версий есть на странице релизов.'
    },
    'cl.downloads': { en: 'downloads', ru: 'загрузок' },
    'cl.assets': { en: 'Files', ru: 'Файлы' },
    'cl.notes.none': { en: 'No notes for this release.', ru: 'Описание не заполнено.' },
    'cl.open': { en: 'Open on GitHub', ru: 'Открыть на GitHub' }
  };

  function current() {
    try {
      var saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'en' || saved === 'ru') return saved;
    } catch (e) { /* private mode; fall through to the default */ }
    return 'en';
  }

  function translate(lang) {
    document.documentElement.lang = lang;
    var nodes = document.querySelectorAll('[data-i18n]');
    for (var i = 0; i < nodes.length; i++) {
      var entry = STRINGS[nodes[i].getAttribute('data-i18n')];
      if (entry && entry[lang]) nodes[i].textContent = entry[lang];
    }
    // anything built by a page's own script re-renders itself
    document.dispatchEvent(new CustomEvent('languagechange', { detail: { lang: lang } }));
  }

  function set(lang) {
    try { localStorage.setItem(STORAGE_KEY, lang); } catch (e) { /* not fatal */ }
    translate(lang);
    paintToggle(lang);
  }

  function paintToggle(lang) {
    var buttons = document.querySelectorAll('.lang-option');
    for (var i = 0; i < buttons.length; i++) {
      var mine = buttons[i].getAttribute('data-lang');
      buttons[i].setAttribute('aria-pressed', mine === lang ? 'true' : 'false');
      buttons[i].classList.toggle('active', mine === lang);
    }
  }

  function buildToggle() {
    var holder = document.querySelector('.lang');
    if (!holder) return;
    holder.innerHTML = '';
    ['en', 'ru'].forEach(function (lang) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'lang-option';
      button.setAttribute('data-lang', lang);
      button.title = LABELS[lang];
      button.setAttribute('aria-label', LABELS[lang]);
      button.innerHTML = FLAGS[lang];
      button.addEventListener('click', function () { set(lang); });
      holder.appendChild(button);
    });
  }

  /**
   * Counted nouns, which English gets away with as one form plus an "s" and Russian does
   * not: 1 загрузка, 2 загрузки, 5 загрузок. Without this the Russian side reads "1
   * загрузок", which is the sort of thing that makes a translation feel machine-made.
   *
   * @param forms for ru, [one, few, many]; for en, [singular, plural]
   */
  function plural(count, forms) {
    if (current() !== 'ru') {
      return count === 1 ? forms[0] : forms[1];
    }
    var mod100 = Math.abs(count) % 100;
    var mod10 = mod100 % 10;
    if (mod100 > 10 && mod100 < 20) return forms[2];
    if (mod10 === 1) return forms[0];
    if (mod10 >= 2 && mod10 <= 4) return forms[1];
    return forms[2];
  }

  var PLURALS = {
    downloads: {
      en: ['download', 'downloads'],
      ru: ['загрузка', 'загрузки', 'загрузок']
    },
    releases: {
      en: ['release', 'releases'],
      ru: ['релиз', 'релиза', 'релизов']
    }
  };

  /**
   * Looked up by the pages' own scripts for the strings they build at runtime.
   */
  window.i18n = {
    get: function (key) {
      var entry = STRINGS[key];
      return entry ? (entry[current()] || entry.en) : key;
    },
    /**
     * @param which a key of PLURALS - the noun being counted
     */
    plural: function (count, which) {
      var forms = PLURALS[which];
      return forms ? plural(count, forms[current()] || forms.en) : which;
    },
    lang: current
  };

  function start() {
    buildToggle();
    var lang = current();
    translate(lang);
    paintToggle(lang);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
