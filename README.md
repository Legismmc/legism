<div align="center">

<img width="160" height="160" align="center" src="./docs/logo.png" alt="Legism logo">

<h1>
<a href="https://legism.github.io/">Legism</a>
</h1>

A Minecraft launcher with **no ads and no telemetry**, offline and Ely.by accounts,<br>
and mods and modpacks from **Modrinth, CurseForge, FTB, Technic and ATLauncher** in one click

A fork of [Legacy Launcher](https://llaun.ch/) — **not** endorsed by or affiliated with its team

<p align="center">
<strong>English</strong> | <a href="./README_ru.md">Русский</a>
</p>

<div>

[![Latest release](https://img.shields.io/github/v/release/Legismmc/legism?label=Release&style=for-the-badge&color=8a8a8a)](https://github.com/Legismmc/legism/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Legismmc/legism/total?label=Downloads&style=for-the-badge&color=8a8a8a)](https://github.com/Legismmc/legism/releases)
[![GitHub Repo stars](https://img.shields.io/github/stars/Legismmc/legism?label=Stars&style=for-the-badge&color=8a8a8a)](https://github.com/Legismmc/legism/stargazers)
![No ads](https://img.shields.io/badge/ads-none-8a8a8a?style=for-the-badge)

</div>

</div>

## Screenshots

<details>
  <summary>Show</summary>

  <div align="center">
    <img src="docs/screenshots/instances.png" alt="Instance list" width="640" />
    <img src="docs/screenshots/mods.png" alt="Mod catalog inside an instance" width="640" />
  </div>

</details>

## Features

- **No ads, no telemetry.** Every banner, promoted server and tracking beacon the upstream launcher shipped has been removed
- **Play offline** without a Microsoft account, or sign in with [Ely.by](https://ely.by/) to get your skin in game without any mods
- **Instances** — every build has its own mods, resource packs, shaders, worlds, settings and play time, and never interferes with another
- **Mod catalog** for Modrinth and CurseForge: mods, resource packs, shaders, data packs, with required dependencies installed automatically and **update all** in one click
- **Modpacks from five libraries** — Modrinth, CurseForge, FTB, Technic and ATLauncher — installed with the pack's own icon
- **Modpack updates** with the changelog of every version; your own mods, worlds and settings are kept
- Downloads that **finish**: files are fetched ten at a time, checked against their hashes and retried when the connection drops
- **Discord Rich Presence** showing the instance you are playing
- **One-click launcher updates**
- Crash analysis, local server hosting, per-instance logs, screenshots and server list
- Built-in **proxy settings** for networks that need one
- English and Russian interface
- Builds for **Windows, Linux and macOS** (Intel and Apple Silicon)

## Comparison

| Feature                                        | Legism            | Legacy Launcher | Prism Launcher |
|------------------------------------------------|-------------------|-----------------|----------------|
| Offline mode without a Microsoft account       | ✅                | ✅              | ❌             |
| Ely.by accounts                                | ✅                | ✅              | ❌             |
| No ads or telemetry                            | ✅                | ❌              | ✅             |
| Separate instances                             | ✅                | ❌              | ✅             |
| Built-in Modrinth & CurseForge catalog         | ✅                | ❌              | ✅             |
| FTB, Technic and ATLauncher modpacks           | ✅                | ❌              | ✅             |
| Modpack updates with changelogs                | ✅                | ❌              | ✅             |
| Local server hosting                           | ✅                | ✅              | ❌             |
| Discord Rich Presence                          | ✅                | ❌              | ❌             |
| Based on                                       | Legacy Launcher   | —               | PolyMC         |

## Installation

### Stable releases

Download Legism from the [official website](https://legism.github.io/) or the [GitHub Releases](https://github.com/Legismmc/legism/releases/latest) page:

| System                | File                               |
|-----------------------|------------------------------------|
| Windows               | `Legism_windows_installer.exe`     |
| Windows (portable)    | `Legism_windows_portable.zip`      |
| Linux                 | `Legism_linux.tar.gz`              |
| macOS (Apple Silicon) | `Legism_macos_apple_silicon.dmg`   |
| macOS (Intel)         | `Legism_macos_intel.dmg`           |

Every build carries its own Java runtime — nothing else needs installing. Once installed, the launcher updates itself.

### Development builds

Every commit to `main` is built by [GitHub Actions](https://github.com/Legismmc/legism/actions); the builds are attached to each run as artifacts. They are not meant for most users and may be broken. You have been warned.

## Community & Support

Found a bug or want a feature? Open an issue in [GitHub Issues](https://github.com/Legismmc/legism/issues). Pull requests are welcome.

[![Telegram](https://img.shields.io/badge/Telegram-legismmc-8a8a8a?style=for-the-badge&logo=telegram)](https://t.me/legismmc)
[![Discord](https://img.shields.io/badge/Discord-join-8a8a8a?style=for-the-badge&logo=discord)](https://discord.gg/csBAgdRuv)

## Building from source

Needs a JDK 21. `SHORT_BRAND` must be something upstream does not publish, so the bootstrap never replaces this build with the original one — the published builds use `tgsko`.

Portable build (a folder with `LL.exe` and a bundled JRE):

```bash
SHORT_BRAND=tgsko PORTABLE_ENABLED=true ./gradlew :packages:portable:createPortableBuild
```

Windows installer — prepares the Inno Setup tree, which [Inno Setup 6](https://jrsoftware.org/isdl.php) then compiles:

```bash
SHORT_BRAND=tgsko PORTABLE_ENABLED=true INSTALLER_ENABLED=true ./gradlew :packages:installer:prepareInstaller
```

Linux and macOS builds come from `:packages:linux:createLinuxBuild` and `:packages:dmg:assemble`; [`.github/workflows/build.yml`](.github/workflows/build.yml) shows the exact steps for every platform.

The product name, brand and support email come from `buildSrc/src/main/kotlin/net/legacylauncher/gradle/LegacyLauncherBrandPlugin.kt` and can be overridden with the `PRODUCT_NAME`, `SHORT_BRAND` and `SUPPORT_EMAIL` environment variables.

## Authors

- [tgskoZ](https://github.com/tgskoZ) — developer
- [junekq](https://github.com/junekq) — Java developer

## Other

<ul>
  <li>We <strong>ARE NOT</strong> related to the <a href="https://llaun.ch/">Legacy Launcher</a> team. Please do not send them bug reports about this build.</li>
  <li>We <strong>ARE NOT</strong> collecting your information: the upstream telemetry is switched off and only writes to the local log.</li>
  <li>We <strong>ARE</strong> respecting mod authors: files their authors keep to CurseForge's own app are never downloaded behind their back — the launcher links you to them instead.</li>
  <li>We <strong>ARE</strong> open to contributions.</li>
</ul>

## License

See [LICENSE.txt](LICENSE.txt). The upstream project reserves all rights; this fork inherits those terms and does not relicense anything.
