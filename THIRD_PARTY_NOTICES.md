# Third-party notices

LiteCast (轻投) is distributed under the GNU General Public License v3.0 (see [LICENSE](LICENSE)).
It is a modified version of other GPL-3.0 projects and bundles third-party components that keep
their own licenses. This file lists them; the GPL notice of this project does not replace them.

## Projects this work is based on

| Project | Used for | License |
|---|---|---|
| [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) (commit `a46cca0`) | The AirPlay receiver: service, JNI bridge, audio/video pipeline, advanced settings screen. This repository continues its git history. | GPL-3.0 |
| [小狗便利店投屏 / puppy-store-cast](https://github.com/leiz56638111-create/puppy-store-cast) | DLNA renderer: `Dlna`, `Xml`, `HttpServer`, `SsdpServer`, `MediaProbe` in `app/src/main/java/io/github/litecast/dlna/` were ported from it and modified | GPL-3.0-only |
| [Reasno/sony-airplay-tv](https://github.com/Reasno/sony-airplay-tv) | Ideas for always-on receiving on Sony TVs (sticky service, boot start) | GPL-3.0 |
| [Xecxes/SonyCast-AndroidTV](https://github.com/Xecxes/SonyCast-AndroidTV) | Reference for the TV experience | GPL-3.0 |

### Modifications

Files taken from jqssun/android-airplay-server were modified by LiteCast contributors in 2026.
Every change is marked with a `LiteCast:` comment; `git log a46cca0..` shows the full history of
modifications. Ported files from puppy-store-cast carry a header naming their origin.

## Native components (built from source, `app/src/main/cpp/third_party`)

| Component | Version | License |
|---|---|---|
| [UxPlay](https://github.com/FDH2/UxPlay) | v1.73.7 (`df67c21`), with the patches in `app/src/main/cpp/patches/UxPlay` | GPL-3.0. Contains code from RPiPlay (GPL-3.0), ShairPlay and AirplayServer (LGPL-2.1+), PlayFair (GPL-3.0) and llhttp (MIT) |
| [OpenSSL](https://www.openssl.org) via [openssl-cmake](https://github.com/viaduck/openssl-cmake) | OpenSSL 3.4.4 | Apache-2.0 (openssl-cmake: MIT) |
| [FFmpeg](https://ffmpeg.org) | 8.1.2 (`38b8833`), only `libavcodec` with the AAC and ALAC decoders, no GPL parts enabled | LGPL-2.1-or-later |
| [libplist](https://github.com/libimobiledevice/libplist) | `f41b1ea` | LGPL-2.1-or-later |

## Libraries (from Maven)

| Library | License |
|---|---|
| AndroidX (Core, Activity, Lifecycle, DataStore, Media, Compose, Hilt Navigation) | Apache-2.0 |
| AndroidX Media3 (ExoPlayer, HLS, DASH, Transformer, UI) | Apache-2.0 |
| Dagger / Hilt | Apache-2.0 |
| Kotlin standard library, kotlinx.coroutines | Apache-2.0 |
| Oboe | Apache-2.0 |

Test-only dependencies (JUnit, Robolectric, Roborazzi) are not part of the app.

## Trademarks

AirPlay is a trademark of Apple Inc. DLNA and UPnP are named only to describe interoperable
protocols. LiteCast is not affiliated with Apple, Sony, 乐播 (Lebo), 当贝 or any app mentioned in
the documentation.
