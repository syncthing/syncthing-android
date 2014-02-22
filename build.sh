#!/bin/bash

set -e

mkdir -p libs/armeabi-v7a
mkdir -p obj/local/armeabi-v7a
CC="$NDK_ROOT/bin/arm-linux-androideabi-gcc"
CC=$CC GOOS=linux GOARCH=arm GOARM=7 CGO_ENABLED=1 ../go/bin/go install $GOFLAGS -v -ldflags="-android -shared -extld $CC -extldflags '-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16'" -tags android github.com/calmh/syncthing
cp $GOPATH/bin/linux_arm/syncthing libs/armeabi-v7a/libsyncthing.so
cp $GOPATH/bin/linux_arm/syncthing obj/local/armeabi-v7a/libsyncthing.so
