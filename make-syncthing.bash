#!/usr/bin/env bash

set -e

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JNIDIR="/src/main/jniLibs"

# The following android gcc executables can be found in the bin folder of your standalone android ndk toolchain
armAndroidGcc="arm-linux-androideabi-gcc"
arm64AndroidGcc="aarch64-linux-android-gcc"
x86AndroidGcc="x86-gcc"

function checkAndroidToolchain() {
        set +e
        `$1 > /dev/null 2>&1`
        if [ $? == 127 ]; then
                printf "\nYou need to install a standalone android ndk toolchain to build Syncthing for Android. If you have already installed it, please add its bin folder to your PATH.\n\n"
                echo "For a fresh installation refer the following links:"
	        echo "    1. https://developer.android.com/ndk/guides/standalone_toolchain.html"
	        echo "    2. https://gist.github.com/calmh/20c24afc283656695b76f7822c8c8997"
	        exit 1
        fi
        set -e
}


case "$1" in
    arm)
        export CGO_ENABLED=1
        checkAndroidToolchain $armAndroidGcc
        export CC=$armAndroidGcc
        export GOOS=android
        export GOARCH=arm
        export GOARM=5
        export TARGETDIR=$MYDIR$JNIDIR/armeabi
        ;;
    arm64)
        export CGO_ENABLED=1
        checkAndroidToolchain $arm64AndroidGcc
        export CC=$arm64AndroidGcc
        export GOOS=android
        export GOARCH=arm64
        unset GOARM
        export TARGETDIR=$MYDIR$JNIDIR/arm64-v8a
        ;;
    386)
        export CGO_ENABLED=1
        checkAndroidToolchain $x86AndroidGcc
        export CC=$x86AndroidGcc
        export GOOS=android
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
