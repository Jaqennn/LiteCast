# Changelog

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
