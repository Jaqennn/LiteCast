# Changelog

## 0.2.5

- Fixed the player crash when a receiver disconnects while Compose still holds an active playback flag. Receiver changes now replace the remembered session, nullable collectors reset immediately and media views require a live service.
- Preserve an actual crash's ID and timestamp when adding native breadcrumbs, so retry/view acknowledgements survive later starts. Informational previous-process breadcrumbs remain available through Feedback and do not automatically open the diagnostic page.
- Added frame-by-frame flow ownership checks, real video-player unbind/rebind regression and cross-process crash acknowledgement checks on API 21/22/23/31.

## 0.2.4

- Default AirPlay and DLNA names are now simply “轻投”, without TV brand/model prefixes. Upgrade recognises old generated names and preserves custom names and receiver settings.
- Rename supports the keyboard Done action, an explicit remote Down → Save focus path and visible focused buttons. Blank names cannot be saved; Restore default name saves “轻投” directly.
- Added migration and remote/IME rename regression coverage; use Maven Central's canonical endpoint for Robolectric test dependencies.

## 0.2.3

- Removed the feedback survey QR code and survey links; focus the diagnostic report button and retain device details and photo feedback guidance.
- Fixed AirPlay JNI thread ownership, session shutdown and device information data; persist bounded native diagnostic stages.
- Automatically use software audio decoding on Android 5.0/5.1; flush stale audio on stream changes and retry video decoder startup failures.
- Added native thread/audio buffer regression checks and AAC-LC/AAC-ELD decoding coverage across API 21/22/23/31.
- Keep automatic audio buffering enabled. Slow audio recovery after seeking and freezes during some DLNA-to-mirroring transitions remain under investigation.

## 0.2.2

- Fixed Android 5.x native startup and added photo-ready diagnostic reports with recovery across process restarts.


- English: the notification now says "LiteCast is running · Receiving AirPlay and DLNA casts"
  instead of the upstream "AirPlay Server Running", and the auto-start settings say they start
  both AirPlay and DLNA (the Chinese text says the same)

## 0.2.1

- A Feedback button on the home screen (and Feedback in Settings) opens a page with the survey's
  QR code and this TV's brand, model, Android version and LiteCast version, read from the system,
  so people copy them into the survey instead of looking them up. The survey needs no account and
  opens in mainland China; the QR code is bundled, nothing is sent from the TV
- A cast that fails for good now says where to send feedback

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
