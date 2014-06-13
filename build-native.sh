#!/bin/bash

cd "$GOPATH/src/github.com/calmh/syncthing/"

./build.sh test || exit 1
./build.sh assets

export GOOS=linux
export ENVIRONMENT=android

export GOARCH=386

./build.sh
cp syncthing syncthing-x86

export GOARCH=arm

export GOARM=7
./build.sh
cp syncthing syncthing-armeabi-v7a

export GOARM=5
./build.sh
cp syncthing syncthing-armeabi

#export GOARCH=mips
#./build.sh
#cp syncthing syncthing-mips
