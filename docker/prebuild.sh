#!/bin/bash -e

[ -z "$SYNCTHING_ANDROID_PREBUILT" ] && echo "Prebuild disabled" && exit 0

echo "Prepopulating gradle and go build/pkg cache"
cd syncthing-android
./gradlew --no-daemon lint buildNative
cd ..
rm -rf syncthing-android
