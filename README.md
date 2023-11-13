# syncthing-android

[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
[![Bountysource](https://api.bountysource.com/badge/tracker?tracker_id=1183310)](https://www.bountysource.com/teams/syncthing-android)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android.

<img src="app/src/main/play/listings/en-GB/graphics/phone-screenshots/screenshot_phone_1.png" alt="screenshot 1" width="200" /> <img src="app/src/main/play/listings/en-GB/graphics/phone-screenshots/screenshot_phone_2.png" alt="screenshot 2" width="200" /> <img src="app/src/main/play/listings/en-GB/graphics/phone-screenshots/screenshot_phone_3.png" alt="screenshot 3" width="200" />

[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid) [<img alt="Get it on F-Droid" src="https://f-droid.org/badge/get-it-on.png" height="80">](https://f-droid.org/app/com.nutomic.syncthingandroid)

# Status: "Maintenance mode" - Co-maintainers welcome

tl;dr: The app is still kept up to date, and contributions are still welcome -
however even reviews for those can take a long time. Co-maintainers are very
welcome - get in touch if you are interested.

No-one is dedicating significant time into development or reviews. It's still
kept up to date with Syncthing, Android and dependencies under the wider
Syncthing project umbrella on a best effort basis. Contributions are reviewed,
however available time for that is scarce so it will take a while. And obviously it
depends both on the size/clarity of the change and (admittedly subjective)
relevance of it - chance of successful and speedier reviews is higher if your
change is targeted and small.

## No feature request taken (feature contributions case-by-case)

Handling feature requests use up the little time that is present to keep the app up-to-date, and there is no feature development happening. So unless you are opening a feature request to discuss your own contribution before jumping into coding, the request will be closed directly with some template answer pointing at this section.

# Translations

The project is translated on [Hosted Weblate](https://hosted.weblate.org/projects/syncthing/android/).

## Dev

Language codes are usually mapped correctly by Weblate itself.  The supported
set is different between [Google Play][1] and Android apps.  The latter can be
deduced by what the [Android core framework itself supports][2].  New languages
need to be added in the repository first, then appear automatically in Weblate.

[1]: https://support.google.com/googleplay/android-developer/table/4419860
[2]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/

# Building

These dependencies and instructions are necessary for building from the command
line. If you build using Docker or Android Studio, you don't need to set up and
follow them separately.

## Dependencies

1. Android SDK and NDK
    1. Download SDK command line tools from https://developer.android.com/studio#command-line-tools-only.
    2. Unpack the downloaded archive to an empty folder. This path is going
       to become your `ANDROID_HOME` folder.
    3. Inside the unpacked `cmdline-tools` folder, create yet another folder
       called `latest`, then move everything else inside it, so that the final
       folder hierarchy looks as follows.
       ```
       cmdline-tools/latest/bin
       cmdline-tools/latest/lib
       cmdline-tools/latest/source.properties
       cmdline-tools/latest/NOTICE.txt
       ```
    4. Navigate inside `cmdline-tools/latest/bin`, then execute
       ```
       ./sdkmanager "platform-tools" "build-tools;<version>" "platforms;android-<version>" "extras;android;m2repository" "ndk;<version>"
       ```
       The required tools and NDK will be downloaded automatically.

        **NOTE:** You should check [Dockerfile](docker/Dockerfile) for the
        specific version numbers to insert in the command above.
2. Go (see https://docs.syncthing.net/dev/building#prerequisites for the
   required version)
3. Java version 11 (if not present in ``$PATH``, you might need to set
   ``$JAVA_HOME`` accordingly)
4. Python version 3

## Build instructions

1. Clone the project with
   ```
   git clone https://github.com/syncthing/syncthing-android.git --recursive
   ```
   Alternatively, if already present on the disk, run
   ```
   git submodule init && git submodule update
   ```
   in the project folder.
2. Make sure that the `ANDROID_HOME` environment variable is set to the path
   containing the Android SDK (see [Dependecies](#dependencies)).
3. Navigate inside `syncthing-android`, then build the APK file with
   ```
   ./gradlew buildNative
   ./gradlew assembleDebug
   ```
4. Once completed, `app-debug.apk` will be present inside `app/build/outputs/apk/debug`.

**NOTE:** On Windows, you must use the Command Prompt (and not PowerShell) to
compile. When doing so, in the commands replace all forward slashes `/` with
backslashes `\`.

# License

The project is licensed under the [MPLv2](LICENSE).
