#!/bin/bash -e

[ -z "$SYNCTHING_ANDROID_PREBUILT" ] && echo "Prebuild disabled" && exit 0

echo "Prepopulating gradle and go build/pkg cache"
git clone --recurse-submodules https://github.com/syncthing/syncthing-android
cd syncthing-android
./gradlew --no-daemon lint build
cd ..
rm -rf syncthing-android
