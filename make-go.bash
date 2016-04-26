#!/usr/bin/env bash

set -e

RESET=1

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export CGO_ENABLED=0

if [ -z "$GOROOT_BOOTSTRAP" ]; then
    # We need Go 1.4 to bootstrap Go 1.5
    if [ -z $GOROOT ] || [[ $(go version) != go\ version\ go1.4* ]] ; then
            git submodule update --init ext/golang/go1.4
            # Build Go 1.4 for host
            pushd ext/golang/go1.4/src
            ./make.bash --no-clean
            popd
            # Add Go 1.4 to the environment
            export GOROOT="$(pwd)/ext/golang/go1.4"
    fi
    # Add Go 1.4 compiler to PATH
    export GOROOT_BOOTSTRAP=$GOROOT
fi

case "$1" in
    arm)
        export GOOS=linux
        export GOARCH=arm
        export GOARM=5
        ;;
    386)
        export GOOS=linux
        export GOARCH=386
        export GO386=387
        ;;
    amd64)
        export GOOS=linux
        export GOARCH=amd64
        ;;
    *)
        echo "Must specify either arm or 386 or amd64"
        exit 1
esac

#TODO figure out why --depth 1 never works right
if [ $RESET -eq 1 ]; then
    git submodule update --init ext/golang/go
fi

unset GOPATH

export GOROOT_FINAL=${MYDIR}/ext/golang/dist/go-${GOOS}-${GOARCH}

if [ -d "$GOROOT_FINAL" ]; then
    rm -r "$GOROOT_FINAL"
fi
mkdir -p "$GOROOT_FINAL"

pushd ext/golang/go/src

git reset --hard HEAD

# Apply patches to Golang
for PATCH in $MYDIR/patches/golang/all/*.patch; do
    echo "Applying $PATCH"
    patch -p1 <$PATCH
done

set +e
./clean.bash
rm -r ../bin
rm -r ../pkg
set -e

if [ ! -e ../VERSION ]; then
    echo "$(git describe --tags)" > ../VERSION
fi

./make.bash --no-banner
cp -a ../bin "${GOROOT_FINAL}"/
cp -a ../pkg "${GOROOT_FINAL}"/
cp -a ../src "${GOROOT_FINAL}"/

if [[ $RESET -eq 1 && -e ./make.bash ]]; then
    pushd ../
    git clean -f
    popd
fi

popd

if [ $RESET -eq 1 ]; then
    git submodule update --init ext/golang/go
fi

echo "Complete"

