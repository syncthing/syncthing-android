#!/usr/bin/env bash

set -e

GO_SOURCE_URL="https://storage.googleapis.com/golang/"
GO_BOOTSTRAP_SOURCE="go1.4.3.src.tar.gz"
GO_BOOTSTRAP_SHA1="486db10dc571a55c8d795365070f66d343458c48"
GO_SOURCE="go1.6.3.src.tar.gz"
GO_SHA1="b487b9127afba37e6c62305165bf840758d6adaf"

MYDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export CGO_ENABLED=0

if [ -z "$GOROOT_BOOTSTRAP" ]; then
    # We need Go 1.4 to bootstrap Go 1.5
    if [ -z $GOROOT ] || [[ $(go version) != go\ version\ go1.4* ]] ; then
      if [ ! -f "$GO_BOOTSTRAP_SOURCE" ]; then
        wget "$GO_SOURCE_URL$GO_BOOTSTRAP_SOURCE"
      fi
      sha1sum=$(sha1sum "$GO_BOOTSTRAP_SOURCE" | awk '{print $1}')
      if [ "$GO_BOOTSTRAP_SHA1" != "$sha1sum" ]; then
        echo "Error: invalid checksum"
        exit 1
      fi
      rm -rf ext/golang/go1.4
      mkdir -p ext/golang/go1.4
      tar -xf "$GO_BOOTSTRAP_SOURCE" -C ext/golang/go1.4 --strip 1
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

unset GOPATH

export GOROOT_FINAL=${MYDIR}/ext/golang/dist/go-${GOOS}-${GOARCH}

if [ -d "$GOROOT_FINAL" ]; then
    rm -r "$GOROOT_FINAL"
fi
mkdir -p "$GOROOT_FINAL"

# Fetch latest Go
if [ ! -f "$GO_SOURCE" ]; then
  wget -O "$GO_SOURCE" "$GO_SOURCE_URL$GO_SOURCE"
fi
sha1sum=$(sha1sum "$GO_SOURCE" | awk '{print $1}')
if [ "$GO_SHA1" != "$sha1sum" ]; then
  echo "Error: invalid checksum"
  exit 1
fi
rm -rf ext/golang/go
mkdir -p ext/golang/go
tar -xf "$GO_SOURCE" -C ext/golang/go --strip 1
pushd ext/golang/go/src

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

if [[ -e ./make.bash ]]; then
    pushd ../
    git clean -f
    popd
fi

popd

echo "Complete"

