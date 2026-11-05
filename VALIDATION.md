# Validation - 2026-11-05

## Checks for the initial GitHub import

- `android/gradlew.bat -p android :engine:test :desktop:installDist` passed.
- Engine tests were then rerun with `--rerun-tasks` and `-PsmokeMedia` pointing to a downloaded MP4 with video and audio: **16 tests, 0 skipped, 0 failures, 0 errors**. Coverage includes URL handling, filenames, Instagram anonymous-query responses and metadata, TikTok page data, cancellation cleanup, and splitting / rejoining MP4 tracks without an external executable.
- `node tests/test_extension.cjs` passed. These are mocked browser-API tests covering progress, completion, reset, concurrency, disconnects, and outdated-host detection; they do not constitute a loaded-browser end-to-end test.
- `android/gradlew.bat -p android :app:assembleCompact` passed, including R8/resource shrinking and the compact lint check. The local SDK warned that platform-tools was missing / its license was not accepted; this did not prevent APK assembly.
- `tests/Smoke-Windows.ps1` passed against the packaged native host: downloaded the public W3Schools MP4 (788,493 bytes), emitted four framed progress updates, saved the video, and exited successfully. Settings and output were isolated in a temporary folder.

## Download fixes included

- Instagram resolves public post / reel shortcodes through an anonymous GraphQL request, using an anonymous session token and browser request headers. Page and embed metadata remain fallbacks. Fixtures cover tokens, media IDs, response parsing, carousel selection, and unavailable media.
- Android YouTube merging preloads mp4parser's `isoparser2-default.properties` through the engine classloader. The library's system-classloader lookup cannot see APK resources and otherwise throws `inStream parameter is null`.
- In the earlier investigation, an isolated desktop classloader reproduced that exact merge error twice with the old engine. The fixed engine downloaded and merged a 480p YouTube video (17.5 MB). This simulates resource visibility; it is not a phone test.
- Android is version **2.0.2**, code **5**, and the universal APK contains no native runtime binaries. The local test APK was rebuilt using the existing debug signing key. Other developers' debug keys will differ.

## Earlier Windows checks

- The packaged host downloaded a public YouTube video (`https://www.youtube.com/watch?v=jNQXAC9IVRw`, 629,172 bytes) with live progress.
- Explorer's COM interface confirmed the actual configured folder and selected video. A separate fixture checked spaces, commas, accents, and emoji in folder and file names.
- Windows packaging uses jpackage with a trimmed OpenJDK 21 runtime and separate GUI / native-messaging launchers. Browser registration and user settings are preserved when rebuilding locally.

## Remaining limits

No connected Android phone was used. Android 13 sharing, notifications, gallery publication, lifecycle behavior, and device-specific merging still need device testing. Instagram has fixture coverage; this validation record does not establish live success across desktop and Android. TikTok also needs live end-to-end verification. Website restrictions and extractor changes can cause failures even when fixtures pass.

The engine supports MP4 and a narrower set of sites / formats than yt-dlp. There is no native Windows Share target. See the README for installation, signing, and supported-link limits.
