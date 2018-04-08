# How to use this

1. Build the docker image: `docker build -t syncthing-android-builder:latest .`
2. Checkout syncthing-android somewhere (for the sake of discussion let's say /tmp/syncthing-android)
3. Run `docker run -it --rm -v /tmp/syncthing-android:/mnt syncthing-android-builder ./gradlew buildNative assembleDebug`
4. Retrieve APKs from /tmp/syncthing-android/app/build/outputs
