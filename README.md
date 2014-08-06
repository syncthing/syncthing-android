## syncthing-android

A wrapper of [syncthing](https://github.com/syncthing/syncthing) for Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Building

Use `gradlew assembleDebug` to compile the APK.

Note: Gradlew is a gradle wrapper which allows to specify the gradle version. Use `gradle createWrapper` to create your own gradlew instance.

The build process follows three phases:
- It downloads and compiles Golang v1.2 for x86 and ARM cross-compilation: Syncthing-android depends on Syncthing "native" (https://github.com/syncthing/syncthing) and this requires Go v1.2. In order to create the APK for x86 and ARM we need to cross compile this library and we use the scripts provided by https://github.com/davecheney/golang-crosscompile.
- The Syncthing native libraries are compiled for the different architectures using `gradlew buildNative`.
- The final APK is built using the `gradlew assembleDebug` task.


## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
The golang-crosscompile is provided by the Go Authors [BSD 3-Clause License][https://github.com/davecheney/golang-crosscompile/blob/e925635a41997e4258fe86bfaf127e84e54ed806/LICENSE]
