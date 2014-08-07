## syncthing-android

A wrapper of [syncthing](https://github.com/calmh/syncthing) for Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)


## Building

There are multiple ways to get the native syncthing binary:
- open a syncthing apk running on your device as a zip, extract the `lib/` folder into your project directory and rename it to `libs/`.
- Depending on your target architecture, download `syncthing-linux-386`, `syncthing-linux-armv5`, `syncthing-linux-armv7` or `syncthing-linux-mips` from [syncthing releases](https://github.com/calmh/syncthing/releases), and extract the binary to `libs/x86/libsyncthing.so`, `libs/armeabi-v7a/libsyncthing.so`, `libs/armeabi/libsyncthing.so` or `libs/mips/libsyncthing.so` respectively.
- Set up a syncthing compile and run `gradle buildNative` in your syncthing-android directory.

Then, run `gradle assembleDebug`.

## Troubleshooting

- You must install the Android Support Repository if you get an error similar to the one below. You can do it via `./android` (in $ANDROID_HOME/tools) and then select and install under extras or `android update sdk --no-ui --filter extra`

  > A problem occurred configuring root project 'syncthing-android'.
  > Could not resolve all dependencies for configuration ':_armeabiDebugCompile'.
     > Could not find any version that matches com.android.support:appcompat-v7:19.1.+.
       Required by:
           :syncthing-android:unspecified`


## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
