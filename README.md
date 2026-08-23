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
| `stats.llaun.ch` telemetry | disabled — `Stats` only writes to the local log now |

## What was added

`net.legacylauncher.modrinth` — a small client for the Modrinth v2 API plus a
launcher scene that lets you search for mods, pick a game version and mod
loader, and install (or remove) them. Downloads are verified against the
SHA-512 hash Modrinth publishes, and required dependencies are pulled in
automatically.

Open it from the launcher with the puzzle-piece button next to *Play*.

## Building

Same as upstream:

```
./gradlew build
```

The product name, brand and support email come from
`buildSrc/src/main/kotlin/net/legacylauncher/gradle/LegacyLauncherBrandPlugin.kt`
and can be overridden with the `PRODUCT_NAME`, `SHORT_BRAND` and
`SUPPORT_EMAIL` environment variables.

> **Note:** the `lang` resources (`launcher/src/main/resources/net/legacylauncher/lang`)
> live in a git submodule that is not part of this source archive. Without it the
> launcher falls back to showing raw translation keys. The Modrinth UI does not
> depend on it — it carries its own English/Russian strings.

## Licence

See [LICENSE.txt](LICENSE.txt). The upstream project reserves all rights; this
fork inherits those terms and does not relicense anything.
