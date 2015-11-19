#!/usr/bin/env bash

set -e

RESET=1

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -z "$ANDROID_NDK" ]; then
    echo "Error: unspecified ANDROID_NDK"
    exit 1
fi

case "$1" in
    arm)
        if [ ! -d "${MYDIR}/build/ndk-$1" ]; then
          sh ${ANDROID_NDK}/build/tools/make-standalone-toolchain.sh --platform=android-9 --toolchain=arm-linux-androideabi-4.9 --install-dir=${MYDIR}/build/ndk-$1
        fi
        export CC=${MYDIR}/build/ndk-$1/bin/arm-linux-androideabi-gcc
        export CXX=${MYDIR}/build/ndk-$1/bin/arm-linux-androideabi-g++
        export CGO_ENABLED=1
        export GOOS=android
        export GOARCH=arm
        export GOARM=5
        export TARGETDIR=${MYDIR}/libs/armeabi
        ;;
    386)
        if [ ! -d "${MYDIR}/build/ndk-$1" ]; then
          sh ${ANDROID_NDK}/build/tools/make-standalone-toolchain.sh --platform=android-9 --toolchain=x86-4.9 --install-dir=${MYDIR}/build/ndk-$1
        fi
        export CC_FOR_TARGET=${MYDIR}/build/ndk-$1/bin/i686-linux-android-gcc
        export CXX_FOR_TARGET=${MYDIR}/build/ndk-$1/bin/i686-linux-android-g++
        export CGO_ENABLED=1
        export GOOS=android
        export GOARCH=386
        export GO386=387
        export TARGETDIR=${MYDIR}/libs/x86
        ;;
    amd64)
        if [ ! -d "${MYDIR}/build/ndk-$1" ]; then
          sh ${ANDROID_NDK}/build/tools/make-standalone-toolchain.sh --platform=android-9 --toolchain=x86_64-4.9 --install-dir=${MYDIR}/build/ndk-$1
        fi
        export CC_FOR_TARGET=${MYDIR}/build/ndk-$1/bin/x86_64-linux-android-gcc
        export CXX_FOR_TARGET=${MYDIR}/build/ndk-$1/bin/x86_64-linux-android-g++
        export CGO_ENABLED=1
        export GOOS=android
        export GOARCH=amd64
        export TARGETDIR=${MYDIR}/libs/x86_64
        ;;
    *)
        echo "Must specify either arm or 386 or amd64"
        exit 1
esac

unset GOPATH #Set by build.go
export GOROOT=${MYDIR}/ext/golang/dist/go-${GOOS}-${GOARCH}
export PATH=${GOROOT}/bin:${PATH}

if [ ! -x ${GOROOT}/bin/${GOOS}_${GOARCH}/go ]; then
    echo Need to build go for ${GOOS}-${GOARCH}
    exit 1
fi

if [ $RESET -eq 1 ]; then
    git submodule update --init ext/syncthing/src/github.com/syncthing/syncthing
fi

pushd ext/syncthing/src/github.com/syncthing/syncthing

_GOOS=$GOOS
unset GOOS
_GOARCH=$GOARCH
unset GOARCH

go run build.go -goos=${_GOOS} -goarch=${_GOARCH} clean
go run build.go -goos=${_GOOS} -goarch=${_GOARCH} -no-upgrade build

export GOOS=$_GOOS
export GOARCH=$_GOARCH

mkdir -p ${TARGETDIR}
mv syncthing ${TARGETDIR}/libsyncthing.so
chmod 644 ${TARGETDIR}/libsyncthing.so

if [[ RESET -eq 1 && -e ./build.go ]]; then
    git clean -f
fi

popd

if [ $RESET -eq 1 ]; then
    git submodule update --init ext/syncthing/src/github.com/syncthing/syncthing
fi

echo "Build Complete"

