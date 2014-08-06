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
	TMPGO='build/go1.2'
	if [ ! -d "$TMPGO" ]; then
		# Download GOLANG
		hg clone https://code.google.com/p/go/ -r 9c4fdd8369ca4483fbed1cb8e67f02643ca10f79 "$TMPGO"
		# Build GO for host
		cd $TMPGO/src
		./make.bash
		export GOROOT="$ORIG/$TMPGO"
		# Build GO for cross-compilation
		source "$ORIG/ext/golang-crosscompile/crosscompile.bash"
		go-crosscompile-build linux/386
		go-crosscompile-build linux/arm
		cd "$ORIG"
	fi

	export GOROOT="$(readlink -e $TMPGO)"
fi

export PATH="$GOROOT/bin":$PATH

# Compile syncthing
cd "ext/syncthing/"
export GOPATH="$(readlink -e .)"

# Install godep
$GOROOT/bin/go get github.com/tools/godep
export PATH="$(readlink -e bin)":$PATH

# Install dependencies
./build.sh setup || true

# Build test
#./build.sh test || exit 1

export GOOS=linux
export ENVIRONMENT=android

# X86
export GOARCH=386
./build.sh "" -tags noupgrade
cp bin/syncthing $ORIG/bin/syncthing-x86


# ARM
export GOARCH=arm

export GOARM=7
./build.sh "" -tags noupgrade
cp bin/linux_arm/syncthing $ORIG/bin/syncthing-armeabi-v7a

export GOARM=5
./build.sh "" -tags noupgrade
cp bin/linux_arm/syncthing $ORIG/bin/syncthing-armeabi


# MIPS
#export GOARCH=mips
#./build.sh "" -tags noupgrade
#cp bin/syncthing $ORIG/bin/syncthing-mips


# Cleanup if succeeded
cd $ORIG
if ls bin/syncthing-* >/dev/null ; then
	rm -rf build/
fi
