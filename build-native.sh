#!/bin/bash

cd "$GOPATH/src/github.com/syncthing/syncthing/"

./build.sh test || exit 1

export GOOS=linux
export ENVIRONMENT=android

export GOARCH=386

./build.sh "" -tags noupgrade
cp syncthing syncthing-x86

export GOARCH=arm

export GOARM=7
./build.sh "" -tags noupgrade
cp syncthing syncthing-armeabi-v7a

export GOARM=5
./build.sh "" -tags noupgrade
cp syncthing syncthing-armeabi

#export GOARCH=mips
#./build.sh "" -tags noupgrade
#cp syncthing syncthing-mips
