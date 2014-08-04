## syncthing-android

A wrapper of [syncthing](https://github.com/calmh/syncthing) for Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Building

To build everyhing from scratch, please run ./build-syncthing-android.sh
The process consists of three phases:
- Download and compile Golang v1.2 for x86 and ARM cross-compilation: Syncthing-android depends on Syncthing "native" (https://github.com/calmh/syncthing) and this requires Go v1.2. In order to create the APK for x86 and ARM we need to cross compile this library and these scripts are provided by https://github.com/davecheney/golang-crosscompile.
- Compile Syncthing native for the different architectures using `gradle buildNative`. A compatible gradle wrapper provides gradle v1.10.
- Compile the final APK using `gradle assembleDebug`.

## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
