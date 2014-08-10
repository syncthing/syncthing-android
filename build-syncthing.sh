#!/bin/bash -e

# Build the syncthing library
ORIG=$(pwd)
mkdir -p bin

# Load submodules
git submodule update --init --recursive

# Check for GOLANG installation
if [ -z $GOROOT ]; then
	# GOLANG v1.3 not support yet, using 1.2
	mkdir -p "build"
	tmpgo='build/go1.2'
	if [ ! -d "$tmpgo" ]; then
		# Download GOLANG
		sha1=wget -O - http://golang.org/dl/go1.2.2.src.tar.gz |\
			tee go.tar.gz | openssl dgst -sha1 | cut -d ' ' -f 2
		if [ "$sha1" != "3ce0ac4db434fc1546fec074841ff40dc48c1167" ]; then
			echo "go.src.tar.gz SHA1 checksum does not match!"
			exit 1
		fi
		tar -xzf go.tar.gz --strip=1 -C $tmpgo
		# Build GO for host
		pushd $tmpgo/src
		./make.bash
		# Add GO to the environment
		export GOROOT="$(readlink -e ..)"
		export PATH=$PATH:$GOROOT/bin
		# Build GO for cross-compilation
		source "$ORIG/ext/golang-crosscompile/crosscompile.bash"
		go-crosscompile-build linux/386
		go-crosscompile-build linux/arm
		popd
	fi

	export GOROOT="$(readlink -e $tmpgo)"
fi

export PATH="$GOROOT/bin":$PATH

# Setup GOPATH
cd "ext/syncthing/"
export GOPATH="$(readlink -e .)"

# Install godep
$GOROOT/bin/go get github.com/tools/godep
export PATH="$(readlink -e bin)":$PATH

# Install dependencies
cd src/github.com/syncthing/syncthing
./build.sh setup || true

# Build test
#./build.sh test || exit 1

export GOOS=linux
export ENVIRONMENT=android

# X86
GOARCH=386 ./build.sh "" -tags noupgrade
mv bin/linux_386/syncthing $ORIG/bin/syncthing-x86
rm -rf bin

# ARM-7
GOARCH=arm GOARM=7 ./build.sh "" -tags noupgrade
mv bin/linux_arm/syncthing $ORIG/bin/syncthing-armeabi-v7a
rm -rf bin

# ARM-5
GOARCH=arm GOARM=5 ./build.sh "" -tags noupgrade
mv bin/linux_arm/syncthing $ORIG/bin/syncthing-armeabi
rm -rf bin

# Cleanup if succeeded
cd $ORIG
if ls bin/syncthing-* >/dev/null ; then
	rm -rf build/
fi
