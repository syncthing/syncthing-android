## syncthing-android

A port of [syncthing](https://github.com/calmh/syncthing) to Android.

## Building

Setup [goandroid](https://github.com/eliasnaur/goandroid).

Then, apply `go.diff` to your local golang source, and compile it.

For syncthing, use [my fork](https://github.com/Nutomic/syncthing/tree/android).

To compile, run `./build.sh` (go cross compile) and `ant -f build.xml clean debug install run` (Android package).

## License

All code is licensed under the [MIT License](https://github.com/Nutomic/syncthing-android/blob/master/LICENSE).
