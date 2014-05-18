## syncthing-android

A port of [syncthing](https://github.com/calmh/syncthing) to Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Building

There are multiple ways to get the native syncthing binary:
- Depending on your target architecture, download `syncthing-linux-386`, `syncthing-linux-armv5`, `syncthing-linux-armv7` or `syncthing-linux-mips` from [syncthing releases](https://github.com/calmh/syncthing/releases), and extract the binary to `libs/x86/libsyncthing.so`, `libs/armeabi-v7a/libsyncthing.so`, `libs/armeabi/libsyncthing.so` or `libs/mips/libsyncthing.so` respectively.
- Set up a syncthing compile and run `gradle buildNative` in your syncthing-android directory.

Then, run `gradle assembleDebug`.

## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
