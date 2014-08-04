#!/bin/bash

# Build the syncthing library
ORIG=$(pwd)
mkdir -p bin

# Check for GOLANG installation
if [ -z $GOROOT ]; then
	# GOLANG v1.3 not support yet, using 1.2
	mkdir -p "tmp"
	TMPGO='tmp/go1.2'
	if [ ! -d "$TMPGO" ]; then
		# Download GOLANG
		echo "Downloading Golang..."
		hg clone https://code.google.com/p/go/ -r release-branch.go1.2 "$TMPGO"
		# Build GO for host
		cd $TMPGO/src
		./make.bash
		export GOROOT="$ORIG/$TMPGO"
		# Build GO for cross-compilation
		source "$ORIG/submodule/golang-crosscompile/crosscompile.bash"
		go-crosscompile-build linux/386
		go-crosscompile-build linux/arm
		cd "$ORIG"
	fi

	export GOROOT="$(readlink -e $TMPGO)"
fi

export PATH=$PATH:"$GOROOT/bin"

# Compile syncthing
cd "submodule/syncthing/"
export GOPATH="$(readlink -e .)"

# Install godep
$GOROOT/bin/go get github.com/tools/godep
export PATH=$PATH:"$(readlink -e bin)"

# Install dependencies
./build.sh setup

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
cp bin/syncthing $ORIG/bin/syncthing-armeabi-v7a

export GOARM=5
./build.sh "" -tags noupgrade
cp bin/syncthing $ORIG/bin/syncthing-armeabi


# MIPS
#export GOARCH=mips
#./build.sh "" -tags noupgrade
#cp bin/syncthing $ORIG/bin/syncthing-mips

