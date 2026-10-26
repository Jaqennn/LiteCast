# LiteCast (轻投)

**A free, ad-free, open-source casting receiver for Android TV.** Install it on the TV and cast
from an iPhone (screen mirroring, AirPlay video and music) or from the TV / cast button of video
apps (DLNA), on Android and iOS alike. No account, no time limit, no upsell.

It was made for a Chinese-market Sony X90J, whose built-in casting (Lebo / 乐播) started asking
for payment, but it runs on most Android TVs (Android 6.0+, ARM).

Works on Android TV / Google TV sets and boxes from Sony Bravia, TCL, Hisense, Xiaomi and others; tested on a Sony X90J.

[中文说明](README.md) · [Download APK](https://github.com/Jaqennn/LiteCast/releases/latest)

| Home | Video | AirPlay music |
|---|---|---|
| ![Home](docs/screenshots/home.png) | ![Video](docs/screenshots/player-video.png) | ![Music](docs/screenshots/player-music.png) |

## What can be cast

| Source | Works | Notes |
|---|---|---|
| iPhone / iPad / Mac screen mirroring | ✅ | Picture and sound |
| AirPlay video (Photos, Safari, apps that support AirPlay) | ✅ | The TV streams the video itself; the phone can sleep |
| AirPlay music | ✅ | Cover, title and progress; previous / next from the remote |
| The TV / cast button in video apps (DLNA) | ✅ mostly | The app hands over a URL and the TV plays it |
| DRM content (Apple TV app, Netflix, Disney+, …) | ❌ | Only certified receivers get the keys; mirroring shows black |
| Proprietary casting (Lebo cloud cast, Huawei Cast+, Xiaomi, …) | ❌ | Closed protocols |
| Miracast from Android phones | ❌ | Needs system-level Wi-Fi Direct; most TVs have a built-in "Screen mirroring" for it |
| Google Cast | ❌ | Requires Google certification |

## Install

Download the APK from [Releases](../../releases), copy it to a USB drive and open it with the TV's
file manager (allow "unknown sources" for that app when asked), or `adb install -r LiteCast-x.y.z.apk`.

Open LiteCast once. For casts to pop up while you are watching something else, allow
**Display over other apps** (the home screen has a button for it; or
`adb shell appops set io.github.litecast SYSTEM_ALERT_WINDOW allow`). From then on it waits in the
background and starts with the TV.

## Remote

- Video: ←/→ seek (hold to accelerate), OK play/pause, ↑/↓ show progress, Back hide bar / exit
- Mirroring: Back twice disconnects
- Music: OK play/pause, ←/→ previous/next, Back twice disconnects

## How it is built

- AirPlay: [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server), based
  on [UxPlay](https://github.com/FDH2/UxPlay). This repository continues its git history; every
  change is marked `LiteCast:` so upstream updates merge cleanly.
- DLNA: the renderer from [puppy-store-cast](https://github.com/leiz56638111-create/puppy-store-cast),
  made tolerant of the sloppy requests real apps send.
- A TV front end written for the remote: home, settings and a full-screen player that opens for a
  cast and closes itself afterwards.

```bash
git clone --recursive https://github.com/Jaqennn/LiteCast.git
./gradlew assembleDebug        # JDK 21, Android SDK 37, NDK 27.0.12077973, CMake 3.22.1
./gradlew testDebugUnitTest
```

Release signing and CI secrets are described in the [Chinese README](README.md#从源码构建).

## About

Built with AI (Claude) on top of the open-source projects above, from the author's own idea log.
Sister project: [ShiYing](https://github.com/Jaqennn/ShiYing), a Windows GUI video downloader for
YouTube, Bilibili, X and Douyin with AI Chinese subtitles.

## License

[GPL-3.0](LICENSE). Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Not affiliated with Apple, Sony, Lebo or Dangbei. AirPlay is a trademark of Apple Inc.
