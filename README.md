## syncthing-android

A port of [syncthing](https://github.com/calmh/syncthing) to Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Building

Download or execute syncthing for your target platform, put the binary in `libs/armeabi`, `libs/armeabi-v7a`, `libs/mips`, `libs/x86`, (depending on target platform) and rename it to `libsyncthing.so`.

Then, run `gradle assembleDebug`.

## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
