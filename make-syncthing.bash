#!/usr/bin/env bash

set -e

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JNIDIR="/src/main/jniLibs"

case "$1" in
    arm)
        export CGO_ENABLED=1
	# The executable "arm-linux-androideabi-gcc" can be found in the bin folder of your standalone android ndk toolchain
	export CC=arm-linux-androideabi-gcc
        export GOOS=android
        export GOARCH=arm
        export GOARM=5
        export TARGETDIR=$MYDIR$JNIDIR/armeabi
        ;;
    arm64)
        export CGO_ENABLED=1
	# The executable "aarch64-linux-android-gcc" can be found in the bin folder of your standalone android ndk toolchain
	export CC=aarch64-linux-android-gcc
        export GOOS=android
        export GOARCH=arm64
        unset GOARM
        export TARGETDIR=$MYDIR$JNIDIR/arm64-v8a
        ;;
    386)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=386
        export GO386=387
        export TARGETDIR=$MYDIR$JNIDIR/x86
        ;;
    *)
        echo "Invalid architecture"
        exit 1
esac

unset GOPATH #Set by build.go
export PATH=${GOROOT}/bin:${PATH}

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

if [[ -e ./build.go ]]; then
    git clean -f
fi

popd

echo "Build Complete"

