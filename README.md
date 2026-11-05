<div align="center">

# EZDownload

**Save a link. Keep the video.**

A local video downloader for Windows and Android, with browser sharing, live progress, and a shared Java engine.

![Windows 10 / 11](https://img.shields.io/badge/Windows-10%20%2F%2011-0078D4)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84)
![Java 21 build](https://img.shields.io/badge/Build-JDK%2021-orange)
[![GPL v3 or later](https://img.shields.io/badge/License-GPLv3%2B-blue)](LICENSE)

[Get started](#get-started) · [Supported links](#supported-links) · [Build from source](#build-from-source) · [Troubleshooting](#troubleshooting)

</div>

---

## What it does

- **Download on your device.** Links go directly to the source website; no third-party downloader service, account, or subscription is required.
- **Share from your browser.** A Chrome / Edge extension sends pages or links to the Windows app and shows progress on its toolbar icon.
- **Share from Android apps.** Send a link from YouTube, Instagram, or another app to download through a foreground notification.
- **Keep the original quality.** Choose 720p, 1080p, or Best. Separate YouTube video and audio tracks are combined into MP4 without transcoding.
- **Travel light.** No Python, yt-dlp, Node.js, or FFmpeg runtime is required. Windows includes Java; Android uses its own runtime.

The installed app currently appears as **EzSocial Video** / **EzSocialToVideo**. Those names also appear in filenames and settings paths.

## Get started

This repository contains the source. Build it using the instructions below to produce these files; generated binaries are excluded from Git.

| Platform | Build output | Requirements to run |
| --- | --- | --- |
| Windows | `dist/EzSocialToVideo-Windows.zip` | Windows 10 / 11, 64-bit |
| Android | `dist/EzSocialToVideo-Android.apk` | Android 10 or newer |

### Windows

1. Extract the Windows ZIP and open `dist/EzSocialToVideo/EzSocialToVideo.exe` inside it.
2. Choose a destination folder and quality.
3. Paste a video link and start the download.

Keep the entire portable app folder together, including `app` and `runtime`. You do not need to install Java separately. Completed downloads open in File Explorer with the video selected.

<details>
<summary><strong>Set up Chrome / Edge sharing</strong></summary>

1. Open `chrome://extensions` or `edge://extensions` and enable **Developer mode**.
2. Choose **Load unpacked** and select the included `browser-extension` folder.
3. Copy the extension ID and run the registration script from the extracted ZIP or project root:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\Register-Browser.ps1 -ExtensionId YOUR_EXTENSION_ID
   ```

4. Pin **Send to EzSocial**. Click it on a video page, or right-click a link and choose **Download with EzSocial**.

A ring and percentage track progress without opening the downloader window. A green check means the file was saved; a red exclamation mark and notification report an error. Hover over the icon for the current stage. Multiple downloads show their count on the badge.

Registration allows only the specified extension ID to contact the local native host. No local HTTP server is used. If you move the app, register its new path. After updating the extension, reload it from the browser's extensions page; avoid reloading during a download.

To remove integration, run `scripts/Unregister-Browser.ps1`, then remove the extension. This adds a browser toolbar and context-menu action; YouTube's own sharing popup is controlled by YouTube.

</details>

### Android

1. Install the APK, allowing installation from your chosen source when Android prompts you.
2. Open **EzSocial Video** once and allow notifications.
3. In the source app, choose **Share → More / Android share menu → Save video with EzSocial**.

A notification shows download progress. Finished videos appear in **Movies/EzSocial** and your gallery; tap the completion notification to open one. You can also open the launcher app to paste links or change settings.

The APK is universal. The current Android version is **2.0.2** (version code **5**). The `compact` build uses a local debug signing key for testing, not a production release key. Updating an existing install requires the same signing key; another developer's build may require uninstalling the earlier app first.

Downloads queue one at a time. Cancel stops the current queue; later shares can start fresh. Share-menu ordering depends on Android and the source app. Leave enough storage for both temporary files and the final gallery copy. If Android terminates or force-stops the app, share the link again.

## Supported links

| Source | How it works | Scope |
| --- | --- | --- |
| YouTube | NewPipe Extractor retrieves MP4 streams; mp4parser combines separate tracks | Quality is a ceiling, subject to available streams |
| Instagram | Anonymous public-post query with page / embed metadata fallbacks | Public posts and reels; first available video in a carousel |
| TikTok | Reads video URLs from public page data and follows short-link redirects | Best effort; website changes can break extraction |
| Other websites | Direct MP4 links, Open Graph video metadata, HTML video sources, and supported JSON metadata | Pages exposing compatible public media |

Private or login-gated content, age restrictions, rate limits, anti-bot checks, DRM, livestreams, HLS-only media, and unsupported formats may prevent downloads. This engine focuses on MP4 and has narrower coverage than yt-dlp. Site changes require app updates. Download only videos you are allowed to save.

## Build from source

### Prerequisites

- **JDK 21**, with `java` and `jpackage` available on `PATH`.
- **Android SDK** platform **35**, build-tools **35.0.0**, and accepted SDK licenses for Android builds.
- Network access for the included **Gradle 8.14** wrapper and dependencies from Google Maven, Maven Central, and JitPack.
- **Node.js** only if you want to run the extension tests; it is not used by either app.

```powershell
git clone https://github.com/Ze-Chr0ME/EZDownload.git
cd EZDownload

# Build the portable Windows app (also runs engine tests)
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Windows.ps1

# Build the universal Android test APK
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Android.ps1 -AndroidSdk C:\path\to\Android\Sdk

# Package the Windows app, source, extension, and scripts
powershell -ExecutionPolicy Bypass -File .\scripts\Package-Windows.ps1
```

The Android script also accepts `ANDROID_HOME` instead of `-AndroidSdk`. SDK paths, signing keys, build caches, and generated downloads are not tracked. Configure your own release signing before distributing a production Android release.

### Tests

```powershell
# Engine parsing, URL handling, filenames, and cancellation
.\android\gradlew.bat -p android :engine:test

# Browser extension with mocked browser APIs
node .\tests\test_extension.cjs

# Split and rejoin a local MP4 fixture containing video AND audio
.\android\gradlew.bat -p android :engine:test "-PsmokeMedia=C:\path\to\fixture.mp4"

# Live download through the built Windows native host
powershell -ExecutionPolicy Bypass -File .\tests\Smoke-Windows.ps1
```

The live smoke test uses the network and writes to an isolated temporary folder. The MP4 merge test is skipped unless `smokeMedia` is supplied. See [VALIDATION.md](VALIDATION.md) for recorded checks and the remaining device-testing limits.

## Project layout

```text
engine/             Shared Java extraction, network, download, and MP4 merging
android/            Kotlin app, share target, foreground service, Gradle wrapper
desktop/            Windows Java UI and browser native-messaging host
browser-extension/  Chrome / Edge toolbar action and progress display
scripts/            Build, package, and browser registration scripts
tests/              Extension tests and Windows integration checks
```

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| Android reports `inStream parameter is null` | Rebuild / update to 2.0.2. This fixes mp4parser settings-resource loading when combining YouTube video and audio on Android. |
| YouTube reports `Could not get ytInitialData` | Retry the link. If it persists, report the URL and error; extraction can fail when YouTube changes or returns an unexpected response. |
| Instagram cannot return a video | Try the full public post / reel URL. Private content and anonymous-request restrictions may still block it. |
| Browser extension cannot connect | Check the extension ID, run registration again for the current app path, and reload the extension. |
| Android has no visible progress | Open the app and enable notifications in Android settings. |

Windows settings live at `%APPDATA%\EzSocialToVideo\settings.json`; download results and errors are recorded in `background.log` in the same directory. Deleting the portable app leaves your settings and saved videos in place.

For a bug report, include the source URL, platform / OS version, app version, sharing method, and exact error. Avoid posting private links or account credentials.

## License and credits

Distributed under [GNU GPL v3 or later](LICENSE). Built with NewPipe Extractor, OkHttp, Gson, Jsoup, mp4parser, and other open-source libraries. See [THIRD_PARTY.md](THIRD_PARTY.md) for dependency licenses and redistribution details.
