# Legacy by tgsko

An independent fork of [Legacy Launcher](https://llaun.ch/) — a free alternative
Minecraft launcher — with two goals:

* **No advertising.** Every ad surface upstream shipped is gone (see below).
* **Mods in one click.** A built-in [Modrinth](https://modrinth.com/) browser
  installs mods straight into the game directory of the version you selected.

This project is not affiliated with, endorsed by, or supported by the Legacy
Launcher team. Please do not send them bug reports about this build.

## What was removed

| Upstream feature | Status |
| --- | --- |
| `ui.notice` banners, notice side panel, notice scene | deleted |
| Personal notice service (`api.llaun.ch` personal notices) | deleted |
| Promoted servers injected into `servers.dat` | deleted — existing injected entries are cleaned up on launch |
| Promoted store ("buy Minecraft" affiliate link) | replaced with a plain link to minecraft.net |
| Remotely configured notification banner | deleted |
| Ad partner image assets | deleted |
| `stats.llaun.ch` telemetry, including the 30-minute beacon | disabled — `Stats` only writes to the local log now |

## What was added

`net.legacylauncher.modrinth` — a client for the Modrinth v2 API plus a launcher
screen that lets you search for mods, pick a game version and mod loader, and
install or remove them. Downloads are verified against the SHA-512 hash Modrinth
publishes, and required dependencies are pulled in automatically.

Open it with the puzzle-piece button next to *Play*, or from the burger menu.

## Building

Needs a JDK 21. `SHORT_BRAND` must be something upstream does not publish, so the
bootstrap never replaces this build with the original one — the shipped builds use
`tgsko`.

Portable build (a folder with `LL.exe` and a bundled JRE):

```bash
SHORT_BRAND=tgsko PORTABLE_ENABLED=true ./gradlew :packages:portable:createPortableBuild
```

Result: `packages/portable/build/update/tgsko/portable.zip`.

Windows installer — prepares the Inno Setup tree, which
[Inno Setup 6](https://jrsoftware.org/isdl.php) then compiles:

```bash
SHORT_BRAND=tgsko PORTABLE_ENABLED=true INSTALLER_ENABLED=true ./gradlew :packages:installer:prepareInstaller
```

```bash
ISCC.exe packages/installer/build/innosetup/tgsko/main.iss
```

Result: `packages/installer/build/innosetup/tgsko/Output/LegacyByTgsko_tgsko_Installer.exe`.

The product name, brand and support email come from
`buildSrc/src/main/kotlin/net/legacylauncher/gradle/LegacyLauncherBrandPlugin.kt`
and can be overridden with the `PRODUCT_NAME`, `SHORT_BRAND` and `SUPPORT_EMAIL`
environment variables.

## Notes

**Translations.** Upstream keeps them in a git submodule that is not part of this
source archive; without them every label renders as a raw key. The files under
`launcher/src/main/resources/net/legacylauncher/lang` were taken from the upstream
1.169.4 build (whose checksum matches its signed update meta) and rebranded. The
Modrinth screen does not depend on them — it carries its own English and Russian
texts in `ModrinthStrings`.

**VPN and proxy clients.** The launcher follows the system proxy. If a local client
advertises a SOCKS proxy that never answers, every request hangs — Mojang, Ely,
Modrinth alike. Uncomment `-Djava.net.useSystemProxies=false` in `tl.bootargs` to
connect directly instead.

**Support links.** The links in the bootstrap resources and in the installer still
point at `llaun.ch`. They should be pointed at this fork's own channels before the
build is given to anyone else; `packages/installer/resources/common.iss` carries a
TODO for the publisher URL.

## Licence

See [LICENSE.txt](LICENSE.txt). The upstream project reserves all rights; this
fork inherits those terms and does not relicense anything.
