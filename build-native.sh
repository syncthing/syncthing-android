#!/bin/bash

cd "$GOPATH/src/github.com/syncthing/syncthing/"

rm bin/

./build.sh test || exit 1

export GOOS=linux
export ENVIRONMENT=android

GOARCH=386 ./build.sh "" -tags noupgrade
mv bin/linux_386/syncthing bin/syncthing-x86

GOARCH=arm GOARM=5 ./build.sh "" -tags noupgrade
mv bin/linux_arm/syncthing bin/syncthing-armeabi

GOARCH=arm GOARM=7 ./build.sh "" -tags noupgrade
mv bin/linux_arm/syncthing bin/syncthing-armeabi-v7a
