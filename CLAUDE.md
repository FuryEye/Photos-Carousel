# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android home-screen widget (package `com.photoscarousel.widget`) that shows a Google Photos album as a carousel; tapping opens the album in the Google Photos app. Single Gradle module `app`, Kotlin, minSdk 26 / compileSdk 35, JDK 17, AGP 8.2.2, Kotlin 2.0.0. Not a git repository.

## Commands

```bash
./gradlew assembleDebug          # output: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew test                   # no tests exist yet
./gradlew lint
```

The repo is missing `gradle-wrapper.jar` and `gradlew.bat`, so locally on Windows run the cached Gradle 8.10.2 (`~\.gradle\wrapper\dists\gradle-8.10.2-bin\*\gradle-8.10.2\bin\gradle.bat`) with `JAVA_HOME` pointing at a JDK 17+ (e.g. `~\.jdks\jbr-21.0.11`; the PATH `java` is Java 8). CI (`.github/workflows/build-apk.yml`) only runs `assembleDebug` and uploads the APK as `PhotosWidget-Debug-APK`. The debug build uses `applicationIdSuffix = ".debug"`; the release build is minified and signed with the debug key.

## Architecture

Two parallel widget implementations are declared in `AndroidManifest.xml`, each with its own provider XML in `res/xml/`:

- **`PhotoWidgetProvider`** (`AppWidgetProvider` + `RemoteViews`, layout `widget_photos_carousel.xml`) â€” the main, per-widget-instance implementation. `updateWidget()` is a companion function also called by `AlbumPickerActivity`. Shows one photo (Glide `AppWidgetTarget`; local file or `url + =w1000-h1000` for web photos) with title/caption/counter. Prev/next are broadcast `PendingIntent`s (`ACTION_NEXT_PHOTO`/`ACTION_PREV_PHOTO` with `appWidgetId` extra). Auto-advance every 15 s uses a one-shot non-waking `AlarmManager.set(RTC)` alarm (`ACTION_AUTO_ADVANCE`) that re-arms itself in `onReceive` and `onUpdate` (`updatePeriodMillis` can't go below 30 min); it pauses while the phone sleeps and the chain ends when no widgets remain. Index state is in `WidgetPreferencesRepository`. An `AdapterViewFlipper` + `RemoteViewsService` version was tried and abandoned: the launcher showed "Can't load widget" (cause never captured). Request codes are `appWidgetId * 10 + n` to keep PendingIntents distinct per widget.
- **`glance/PhotosGlanceWidget`** (Jetpack Glance) â€” a secondary widget that reads the *global* `getActiveAlbum()`/`getActivePhotoIndex()` rather than per-widget state, and shows text only (no image, no prev/next).

Flow: widget dropped on home screen → `AlbumPickerActivity` (the `APPWIDGET_CONFIGURE` activity) → `GooglePhotosManager` authorizes via Google Identity (scope `photospicker.mediaitems.readonly`), creates a Photos Picker session, opens `pickerUri/autoclose` in the browser/Photos app, polls until `mediaItemsSet`, lists the picked items and downloads them → `WidgetPreferencesRepository.saveSelectedAlbum(appWidgetId, …)` → `PhotoWidgetProvider.updateWidget()` → result `RESULT_OK`.

Key constraints:
- The Google Photos Library API can no longer list a user's albums (removed 2025-03-31), so an "album" here is just the set of photos the user picked (max 2000, the Picker API limit; it is a one-time snapshot, so photos added later in Google Photos never appear without re-picking) — there is no real album deep link; `webUrl` is `https://photos.google.com`.
- Picker `baseUrl`s expire after ~1 hour and need the bearer token, so photos are downloaded (800px) to `filesDir/photos/<appWidgetId>/<timestamp>/` and `Photo.url` is a local file path. Re-picking prunes older dirs; `onDeleted` clears a widget's state.
- Per-widget state (album JSON via Gson, current index) lives in SharedPreferences `widget_prefs`. Gson models are kept by `app/proguard-rules.pro` for the minified release build.
- Auth needs an Android OAuth client in Google Cloud (Photos Picker API enabled, picker scope on the consent screen, test user added) registered for package `com.photoscarousel.widget.debug` (note the debug suffix) and the signing SHA-1. This is untested against the live API.

## Shared-album links (auto-updating)

`AlbumPickerActivity` also accepts a link-shared album URL. `SharedAlbumFetcher` (unofficial, scrapes the public share page for `lh3.googleusercontent.com/pw/...` URLs; may break if Google changes the page) builds an `Album` with `sourceUrl` set and remote `Photo.url`s (loaded by Glide with a `=w1000-h1000` suffix); `webUrl` is the real album link. `PhotoWidgetProvider.onUpdate` (every 30 min via `updatePeriodMillis`) re-fetches in a background thread under `goAsync()` and redraws if the photo list changed. Picker-made albums have `sourceUrl = null` and never refresh. Untested against live Google pages.

## Notes

- `PhotosWidgetApplication` and `MainActivity` are minimal placeholders (MainActivity just shows instructions).
- Dependencies are hardcoded in `app/build.gradle.kts`; `gradle/libs.versions.toml` is used only for plugins, and its versions duplicate the inline ones.

