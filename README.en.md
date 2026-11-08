<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" width="96" alt="LiteCast icon">

# LiteCast (轻投)

**Free, ad-free casting for Android TVs.** Install it on the TV and cast from an iPhone or an Android phone.

[Download APK](https://github.com/Jaqennn/LiteCast/releases/latest/download/LiteCast.apk) · [Install](#install) · [Undo](#undo) · [FAQ](#faq) · [中文说明](README.md)

</div>

| Home | Video | AirPlay music |
|---|---|---|
| ![Home](docs/screenshots/home.png) | ![Video](docs/screenshots/player-video.png) | ![Music](docs/screenshots/player-music.png) |

## Why

- **The TV's built-in casting wants money**: Chinese-market Sony TVs and others ship with Lebo (乐播), which starts asking for a membership. LiteCast began on a Sony X90J like that.
- **iPhones can't cast to most Android TVs**: screen mirroring only speaks AirPlay.
- **Third-party casting apps** want accounts, show ads or cap the time.

## What LiteCast gives you

- Free, no ads, no account, no time limit; open source
- iPhone and Android phones alike: AirPlay (mirroring, video, music) and DLNA (the cast button in video apps) run side by side
- Old TVs too: Android 5.0 and later, on TVs, boxes and projectors
- Install once: it waits in the background, starts with the TV, pops up when you cast and closes when you stop
- Leaves the TV alone: no system settings are changed, it runs next to Lebo, and uninstalling undoes everything
- One APK, under 10 MB, for every TV, 32- and 64-bit

## What can be cast

| From | How | Works |
|---|---|---|
| iPhone / iPad / Mac: screen mirroring | Control Center → Screen Mirroring → "TV name · 轻投" | ✅ Picture and sound |
| iPhone / iPad / Mac: AirPlay video | The AirPlay button in Photos, Safari and AirPlay apps | ✅ The TV streams it; the phone can sleep |
| iPhone / iPad / Mac: AirPlay music | Apple Music and other music apps | ✅ Cover, title and progress |
| Any phone: the TV / cast button in video apps (DLNA) | Bilibili, iQiyi, Tencent Video, Youku, live-streaming apps and more | ✅ Mostly |
| Android phones: system screen mirroring (Miracast) | | ❌ Needs system-level Wi-Fi Direct |
| Huawei Cast+, Xiaomi and other vendor casting, Lebo cloud cast, Google Cast | | ❌ Closed or certified protocols |
| DRM content (Netflix, Disney+, the Apple TV app) | | ❌ Only certified receivers; mirroring shows black |

The app decides the quality: DLNA only hands the TV a URL (Bilibili gives third-party receivers 720p at most, Douyin 1080p).

## Supported TVs

- Android 5.0 or later: Android TV / Google TV, Android-based TVs from any brand, boxes and projectors
- ARM chips (nearly every TV)
- Not supported: Android 4.4 and older; non-Android systems such as LG webOS and Samsung Tizen

Tested: Sony X90J (Chinese market, Android 10), the author's own TV — mirroring, AirPlay video and music and DLNA all work. Android 5.0–5.1 is supported from 0.2.0 but has not been tried on a real TV yet. Please [report your TV](https://github.com/Jaqennn/LiteCast/issues/new?template=device.yml), working or not.

## Download

[Download LiteCast.apk](https://github.com/Jaqennn/LiteCast/releases/latest/download/LiteCast.apk). On the [release page](https://github.com/Jaqennn/LiteCast/releases/latest) it is the **LiteCast.apk** file under "Assets"; the "Source code" entries are the source, not the app.

## Install

- **USB drive**: copy LiteCast.apk to a USB drive and open it with the TV's file manager. If the TV refuses apps from unknown sources, allow that file manager in Settings → Device Preferences → Security & restrictions → Unknown sources (the place differs between brands).
- **An AI agent**: give a desktop agent that can run commands (Claude Code, Codex, …) the guide in [docs/agent.md](docs/agent.md) (in Chinese) and your TV's IP address; it installs, checks and reports back.
- **adb**:
  ```bash
  adb connect <TV IP>:5555
  adb install -r LiteCast.apk
  adb shell appops set io.github.litecast SYSTEM_ALERT_WINDOW allow   # not needed on Android 5.x
  adb shell am start -n io.github.litecast/io.github.litecast.tv.HomeActivity
  ```

**Updating**: install the new LiteCast.apk the same way; it replaces the old version and keeps the settings.

Open LiteCast once. For casts to pop up while you are watching something else, allow **Display over other apps** (the home screen has a button for it). From then on it waits in the background and starts with the TV.

## Remote

- Video: ←/→ seek (hold to accelerate), OK play/pause, ↑/↓ show progress, Back hide bar / exit
- Mirroring: Back twice disconnects
- Music: OK play/pause, ←/→ previous/next, Back twice disconnects

## Undo

LiteCast changes no system settings and disables nothing, so uninstalling restores the TV: Settings → Apps → LiteCast (轻投) → Uninstall, or `adb uninstall io.github.litecast`.

## FAQ

- **The phone doesn't list the TV**: phone and TV must be on the same network (guest networks and AP isolation block discovery); turn off the phone's VPN; on an iPhone, allow Local Network for the video app. On a TV with Lebo it can take a few seconds to appear.
- **A cast doesn't show up**: allow Display over other apps, or open LiteCast first.
- **YouTube keeps loading**: with AirPlay video the TV downloads from YouTube itself, so the TV's network has to reach it; screen mirroring works regardless.
- **Mirroring stutters**: use 5 GHz Wi-Fi for the phone and Ethernet for the TV, or lower the resolution in Settings → Advanced.
- **Security**: no account, no data collected. Casting protocols have no password (AirPlay can require a PIN in settings), so use it on your home network only.
- **Bugs**: [report a problem](https://github.com/Jaqennn/LiteCast/issues/new?template=bug.yml).

## How it is built

- AirPlay: [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server), based
  on [UxPlay](https://github.com/FDH2/UxPlay). This repository continues its git history; every
  change is marked `LiteCast:` so upstream updates merge cleanly.
- DLNA: the renderer from [puppy-store-cast](https://github.com/leiz56638111-create/puppy-store-cast),
  made tolerant of the sloppy requests real apps send.
- A TV front end written for the remote: home, settings and a full-screen player that opens for a
  cast and closes itself afterwards. The install guides follow the example of [Aerial](https://github.com/pengchujin/aerial-tv).

```bash
git clone --recursive https://github.com/Jaqennn/LiteCast.git
./gradlew assembleDebug        # JDK 21, Android SDK 37, NDK 27.0.12077973, CMake 3.22.1
./gradlew testDebugUnitTest
```

To keep Android 5.x, Compose stays on 1.9 (BOM 2025.11.01) and Media3 on 1.8; their newer versions need Android 6.0. Release signing and CI secrets are described in the [Chinese README](README.md#从源码构建).

## About

Built with AI (Claude) on top of the open-source projects above, from the author's own idea log.
Sister project: [ShiYing](https://github.com/Jaqennn/ShiYing), a Windows GUI video downloader for
YouTube, Bilibili, X and Douyin with AI Chinese subtitles.

## License

[GPL-3.0](LICENSE). Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Not affiliated with Apple, Sony, Lebo or Dangbei. AirPlay is a trademark of Apple Inc.
