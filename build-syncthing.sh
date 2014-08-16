#!/bin/bash -e

# Build the syncthing library
ORIG=$(pwd)
mkdir -p bin

# Load submodules
git submodule update --init --recursive

# Check for GOLANG installation
if [ -z $GOROOT ]; then
	mkdir -p "build"
	tmpgo='build/go'
	if [ ! -f "$tmpgo/bin/go" ]; then
		# Download GOLANG v1.3
		wget -O go.src.tar.gz http://golang.org/dl/go1.3.src.tar.gz
		sha1=$(sha1sum go.src.tar.gz)
		if [ "$sha1" != "9f9dfcbcb4fa126b2b66c0830dc733215f2f056e  go.src.tar.gz" ]; then
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
	export GOROOT="$(readlink -e $tmpgo)"
fi

# Add GO compiler to PATH
export PATH=$PATH:$GOROOT/bin

# Check whether GOLANG is compiled with cross-compilation for 386
if [ ! -f $GOROOT/bin/linux_386/go ]; then
	pushd $GOROOT/src
	# Build GO for cross-compilation
	GOOS=linux GOARCH=386 ./make.bash --no-clean
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
