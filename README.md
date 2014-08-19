## syncthing-android

A wrapper of [syncthing](https://github.com/calmh/syncthing) for Android.

[![Get it on Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_60.png)](https://play.google.com/store/apps/details?id=com.nutomic.syncthingandroid)

## Building

To get syncthing app for android running on you device/emulator the native syncthing binary has to be available. There are multiple ways to get the native syncthing binary:
- open a syncthing apk (the one taken from the play store) running on your device as a zip, extract the `lib/` folder into your project directory and rename it to `libs/`.
- Depending on your target architecture, download `syncthing-linux-386`, `syncthing-linux-armv5`, `syncthing-linux-armv7` or `syncthing-linux-mips` from [syncthing releases](https://github.com/calmh/syncthing/releases), and extract the binary to `libs/x86/libsyncthing.so`, `libs/armeabi-v7a/libsyncthing.so`, `libs/armeabi/libsyncthing.so` or `libs/mips/libsyncthing.so` respectively.
- Set up a syncthing compile and run `gradle buildNative` in your syncthing-android directory.

Then, run `gradle assembleDebug`.

## Develop Notes

The syncthing backend used for this android application provides a webinterface by default. It can be acces via the Settings menu -> 'Web GUI'. It is quite helpful to access this web interface from you development machine read [android documentation](http://developer.android.com/tools/devices/emulator.html#redirection) or execute the following steps (assuming you have only one emulator/avd started)
- telnet localhost 5554
- redir add tcp:18080:8080
- Start synchting app on your emulator and access the web ui from you favorite browser of your development machine via [http://127.0.0.1:18080](http://127.0.0.1:18080)

## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
