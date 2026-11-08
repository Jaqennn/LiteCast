# Changelog

## 0.2.0

- Runs on Android 5.0 and later (was 6.0), so older TVs and boxes can install it. Compose stays on
  1.9 and Media3 on 1.8, the last versions that support Android 5; the handful of Android 6.0
  calls (display mode, decoder frame rates, audio devices, active network, mute, overlay
  permission) have fallbacks, and the AirPlay music screen's controls no longer need
  media3-ui-compose-material3
- On a TV whose built-in casting app holds the DLNA discovery port (乐播 on Sony and many others),
  LiteCast announces itself every 4 seconds instead of every 20, so phones list it within seconds
- New release signing key (0.1.x was never published as a download)
- The release file is always called LiteCast.apk, so the README's download link always gets the
  newest version
- Install guide rewritten: what the app solves, what each phone can cast, which Android versions
  work, which file to download, three ways to install (USB drive, an AI agent, adb) and how to
  undo; docs/agent.md walks an AI agent through installing and checking it; issue forms for device
  reports and bugs

## 0.1.1

- The player shows the resolution it is really playing (720P / 1080P / 2K / 4K) next to the
  DLNA / AirPlay label, so it is clear what quality the app sent
- A video that has not started after 15 seconds says why that may be; an AirPlay video whose
  server the TV cannot reach (for example YouTube where it is blocked) says so and suggests
  screen mirroring, instead of spinning and closing silently
- A proxy set on the TV's Wi-Fi is no longer used for LAN and localhost addresses (the AirPlay
  HLS proxy, videos served by phones), so the TV can reach YouTube through a proxy without
  breaking casting

## 0.1.0 — first release

Based on jqssun/android-airplay-server `a46cca0` and the DLNA renderer of puppy-store-cast.

- AirPlay (from upstream): screen mirroring (H.264 / H.265), AirPlay video, AirPlay music with
  cover art, optional PIN
- DLNA renderer: works with the TV / cast button of video apps; accepts requests with missing or
  unquoted SOAPAction, any service version, missing optional arguments and chunked bodies;
  preloads on SetAVTransportURI and starts by itself if the app never sends Play; ignores the
  stray "seek to 0" some apps send right after starting; recognises HLS served with the wrong
  type; retries network errors (2 / 5 / 10 s) and reports expired links clearly
- One player for AirPlay video and DLNA; the newest cast wins
- TV front end: home screen with the name to pick on the phone, settings, a full-screen player
  that pops up for a cast and closes itself afterwards; remote-friendly seeking that accelerates
  while held; Back twice disconnects mirroring
- Always on: background receiver with sticky restart and start on boot; coexists with a
  pre-installed casting app (dynamic AirPlay port, own DLNA port, SSDP announce-only fallback);
  on TVs the wake lock is held only during a cast, so the set still sleeps in standby
- Chinese and English interface
