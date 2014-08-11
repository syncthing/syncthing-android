## syncthing-android

A wrapper of [syncthing](https://github.com/syncthing/syncthing) for Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Building

Use `gradlew assembleDebug` to compile the APK.

Note: Gradlew is a gradle wrapper which allows to specify the gradle version. Use `gradle -b gradle/wrapper/build.xml wrapper` to create your own gradlew instance. Then add it to your path using `export PATH=$PATH:$(pwd)/gradle/wrapper`.

The build process follows three phases:
- It downloads and compiles Golang v1.2 for x86 and ARM cross-compilation: Syncthing-android depends on Syncthing "native" (https://github.com/syncthing/syncthing) and this requires Go v1.2.
- The Syncthing native libraries are compiled for the different architectures using `gradlew buildNative`.
- The final APK is built using the `gradlew assembleDebug` task.


## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
