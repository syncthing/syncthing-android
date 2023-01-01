# syncthing-android

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
[![Bountysource](https://api.bountysource.com/badge/tracker?tracker_id=1183310)](https://www.bountysource.com/teams/syncthing-android)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android.

<img src="app/src/main/play/listings/en-GB/graphics/phone-screenshots/screenshot_phone_1.png" alt="screenshot 1" width="200" /> <img src="app/src/main/play/listings/en-GB/graphics/phone-screenshots/screenshot_phone_2.png" alt="screenshot 2" width="200" /> <img src="app/src/main/play/listings/en-GB/graphics/phone-screenshots/screenshot_phone_3.png" alt="screenshot 3" width="200" />

[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid) [<img alt="Get it on F-Droid" src="https://f-droid.org/badge/get-it-on.png" height="80">](https://f-droid.org/app/com.nutomic.syncthingandroid)

# Translations

The project is translated on [Transifex](https://www.transifex.com/projects/p/syncthing-android/).

## Dev

Language mappings are defined in `.tx/config`, with the second code being the one from transifex. Google play supported languages: https://support.google.com/googleplay/android-developer/table/4419860. Android supported languages: https://stackoverflow.com/questions/7973023/what-is-the-list-of-supported-languages-locales-on-android. If a new language is added on transifex that's not supported, add them to `deleteUnsupportedPlayTranslations` in `app/build.gradle`.

# Building

### Dependencies
- Android SDK, with `$ANDROID_HOME` pointing to it (you can skip this if you are using Android Studio)
- Android NDK (you should install the required version (`ext.ndkVersionShared` in `./build.gradle`) with the usual tools, such that it's located within `$ANDROID_HOME/ndk/`).
- Go (see [here](https://docs.syncthing.net/dev/building.html#prerequisites) for the required version)
- Java Version 11 (you scan skip this if you are using Android Studio, otherwise you might need to set `$JAVA_HOME` accordingly)

### Build instructions

Make sure you clone the project with
`git clone https://github.com/syncthing/syncthing-android.git --recursive`. Alternatively, run
`git submodule init && git submodule update` in the project folder.

Build Syncthing using `./gradlew buildNative`. Then use `./gradlew assembleDebug` or
Android Studio to build the apk.

# License

The project is licensed under the [MPLv2](LICENSE).
