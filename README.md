# MonoTune

A Material 3 YouTube Music client for Android, powered by [Monochrome](https://github.com/nohimazin/Monochrome) as the playback backend.

> [!NOTE]
> MonoTune is a fork of [OuterTune](https://github.com/OuterTune/OuterTune). The codebase is being migrated toward using Monochrome as the primary playback source while retaining YouTube Music search, playlist, likes, and history integrations.

[![License](https://img.shields.io/github/license/nohimazin/MonoTune)](https://www.gnu.org/licenses/gpl-3.0)

## Features

- YouTube Music client features
    - Seamless playback: no ADs & background playback
    - YouTube Music search, playlists, liked songs, and history
    - Account synchronization
- Monochrome playback backend integration (in progress)
    - Track matching and scrobbling queue for Monochrome
- Sleek Material3 design
- Multiple queues
- Synchronized lyrics, and support for word by word/Karaoke lyrics formats (e.g LRC, TTML)
- Audio normalization, tempo/pitch adjustment, and various other audio effects
- Android Auto support
- Support for Android 8 (Oreo) and higher

> [!NOTE]
> Local device audio scanning and offline local file playback are **not** goals for MonoTune.
> MonoTune streams from YouTube Music and will stream from the Monochrome backend once integrated.
> The local-media scanner inherited from OuterTune is still present in the codebase but is
> scheduled for removal — see [BRANCHES.md](./BRANCHES.md) for the current migration status.

> [!NOTE]
> Android 8 (Oreo) and higher is supported.

> [!WARNING]
>
> If you're in a region where YouTube Music is not supported, you won't be able to use this app
***unless*** you have a proxy or VPN to connect to a YTM supported region.

## Building & Contributing

If you would like to help out, or just wish to build the app yourself, please see the
[building and contribution notes](./CONTRIBUTING.md).

For branch and tag structure, see [BRANCHES.md](./BRANCHES.md).

## Attribution

Forked from [OuterTune](https://github.com/OuterTune/OuterTune) by DD3Boh and contributors.

[z-huang/InnerTune](https://github.com/z-huang/InnerTune) for providing the original base, none of this
would have been possible without it.

[Gramophone](https://github.com/FoedusProgramme/Gramophone) for emotional support, and a legendary lyrics parser.

## Disclaimer

This project and its contents are not affiliated with, funded, authorized, endorsed by, or in any
way associated with YouTube, Google LLC or any of its affiliates and subsidiaries.

Any trademark, service mark, trade name, or other intellectual property rights used in this project
are owned by the respective owners.
