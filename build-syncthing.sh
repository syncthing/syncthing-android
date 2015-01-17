#!/bin/bash -e

# Build the syncthing library
ORIG=$(pwd)
mkdir -p bin

# Load submodules
if [ ! -f "ext/syncthing/src/github.com/syncthing/syncthing/.git" ]; then
        git submodule update --init --recursive
fi

# Check for GOLANG installation
if [ -z $GOROOT ] || [[ $(go version) != go\ version\ go1.4* ]] ; then
        mkdir -p "build"
        tmpgo='build/go'
        if [ ! -f "$tmpgo/bin/go" ]; then
                # Download GOLANG v1.4
                wget -O go.src.tar.gz http://golang.org/dl/go1.4.src.tar.gz
                sha1=$(sha1sum go.src.tar.gz)
                if [ "$sha1" != "6a7d9bd90550ae1e164d7803b3e945dc8309252b  go.src.tar.gz" ]; then
                        echo "go.src.tar.gz SHA1 checksum does not match!"
                        exit 1
                fi
                mkdir -p $tmpgo
                tar -xzf go.src.tar.gz --strip=1 -C $tmpgo
                rm go.src.tar.gz
                # Build GO for host
                pushd $tmpgo/src
                ./make.bash --no-clean
                popd
        fi
        # Add GO to the environment
        export GOROOT="$(pwd)/$tmpgo"
fi

# Add GO compiler to PATH
export PATH=$GOROOT/bin:$PATH
# Disable CGO (no dynamic linking)
export CGO_ENABLED=0

# Check whether GOLANG is compiled with cross-compilation for 386
if [ ! -f $GOROOT/bin/linux_386/go ]; then
        pushd $GOROOT/src
        # Build GO for cross-compilation
        GOOS=linux GOARCH=386 GO386=387 ./make.bash --no-clean
        popd
fi

# Check whether GOLANG is compiled with cross-compilation for arm
if [ ! -f $GOROOT/bin/linux_arm/go ]; then
        pushd $GOROOT/src
        # Build GO for cross-compilation
        GOOS=linux GOARCH=arm ./make.bash --no-clean
        popd
fi

# Setup GOPATH
cd "ext/syncthing/"
export GOPATH="$(pwd)"

# Install godep
$GOROOT/bin/go get github.com/tools/godep
export PATH="$(pwd)/bin":$PATH

# Setup syncthing and clean
export ENVIRONMENT=android
cd src/github.com/syncthing/syncthing
$GOROOT/bin/go run build.go clean

# X86
$GOROOT/bin/go run build.go -goos linux -goarch 386 -no-upgrade build
mv syncthing $ORIG/bin/syncthing-x86
$GOROOT/bin/go run build.go clean

# ARM
$GOROOT/bin/go run build.go -goos linux -goarch arm -no-upgrade build
mv syncthing $ORIG/bin/syncthing-armeabi
$GOROOT/bin/go run build.go clean
