## syncthing-android

A wrapper of [syncthing](https://github.com/syncthing/syncthing) for Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Requirements
- sudo apt-get install build-essential
- Android SDK 19+ and the Android Support Repository are required.
- Use `git clone --recursive https://github.com/Nutomic/syncthing-android` to download the source and its submodules.

## Building

Use `gradlew assembleDebug` to compile the APK.

Note: Gradlew is a gradle wrapper which allows to specify the gradle version. Use `gradle -b gradle/wrapper/build.xml wrapper` to create your own gradlew instance. Then add it to your path using `export PATH=$PATH:$(pwd)/gradle/wrapper`.

The build process follows three phases:
- It downloads and compiles Golang v1.3 for x86 and ARM cross-compilation: Syncthing-android depends on Syncthing "native" (https://github.com/syncthing/syncthing) and this requires Go v1.3.
- The Syncthing native libraries are compiled for the different architectures using `gradlew buildNative`.
- The final APK is built using the `gradlew assembleDebug` task.


## Getting Syncthing without building natively

To get Syncthing app for Android running on you device/emulator the native syncthing binary has to be available. There are multiple ways to get the native syncthing binary:
- open the Syncthing apk (the one taken from the play store) running on your device as a zip, extract the `lib/` folder into your project directory and rename it to `libs/`.
- Depending on your target architecture, download `syncthing-linux-386`, `syncthing-linux-armv5`, `syncthing-linux-armv7` or `syncthing-linux-mips` from [syncthing releases](https://github.com/calmh/syncthing/releases), and extract the binary to `libs/x86/libsyncthing.so`, `libs/armeabi/libsyncthing.so`, `libs/armeabi-v7a/libsyncthing.so` or `libs/mips/libsyncthing.so` respectively.


## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
