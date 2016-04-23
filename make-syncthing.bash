#!/usr/bin/env bash

set -e

RESET=1

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JNIDIR="/src/main/jniLibs"

case "$1" in
    arm)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=arm
        export GOARM=5
        export TARGETDIR=$MYDIR$JNIDIR/armeabi
        ;;
    386)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=386
        export GO386=387
        export TARGETDIR=$MYDIR$JNIDIR/x86
        ;;
    amd64)
        export CGO_ENABLED=0
        export GOOS=linux
        export GOARCH=amd64
        export TARGETDIR=$MYDIR$JNIDIR/x86_64
        ;;
    *)
        echo "Must specify either arm or 386 or amd64"
        exit 1
esac

unset GOPATH #Set by build.go
export GOROOT=${MYDIR}/ext/golang/dist/go-${GOOS}-${GOARCH}
export PATH=${GOROOT}/bin:${PATH}

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

