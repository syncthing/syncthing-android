# syncthing-android

[![Build Status](http://android.syncthing.net/job/Syncthing-Android/badge/icon)](http://android.syncthing.net/job/Syncthing-Android/)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for Android.

<img src="src/fat/play/en-GB/listing/phoneScreenshots/screenshot_phone_1.png" alt="screenshot 1" width="200" /> 
<img src="src/fat/play/en-GB/listing/phoneScreenshots/screenshot_phone_2.png" alt="screenshot 2" width="200" /> 
<img src="src/fat/play/en-GB/listing/phoneScreenshots/screenshot_phone_3.png" alt="screenshot 3" width="200" />

[<img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid) [<img alt="Get it on F-Droid" src="https://f-droid.org/badge/get-it-on.png" height="80">](https://f-droid.org/app/com.nutomic.syncthingandroid)

# Translations

The project is translated on [Transifex](https://www.transifex.com/projects/p/syncthing-android/).

Translations can be updated using the [Transifex client](http://docs.transifex.com/developer/client/), using commands `tx push -s` and `tx pull -a`.

# Building

### Requirements
- Android SDK Platform (for the `compileSdkVersion` specified in [build.gradle](build.gradle))
- Android NDK Platform
- Android Support Repository

### Build instructions

Set the `ANDROID_NDK` environment variable to the Android NDK folder (e.g. `export ANDROID_NDK=/opt/android_ndk`).
Build Go and Syncthing using `./make-all.bash`.
Use `./gradlew assembleDebug` in the project directory to compile the APK.

To check for updated gradle dependencies, run `gradle dependencyUpdates`. Additionally, the git submodule in `ext/syncthing/src/github.com/syncthing/syncthing` may need to be updated.


### Building on Windows

To build the Syncthing app on Windows we need to include the native Syncthing binaries:
- Download the `syncthing-linux-386` and `syncthing-linux-arm` archives from [Syncthing releases](https://github.com/syncthing/syncthing/releases) and extract them. In each there is a `syncthing` executable. Rename and place both of these to `libs/x86/libsyncthing.so` and `libs/armeabi/libsyncthing.so` respectively.
Use `./gradlew assembleDebug` in the project directory to compile the APK.

# License

The project is licensed under the [MPLv2](LICENSE).
