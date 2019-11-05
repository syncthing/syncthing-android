#!/bin/bash -e

[ -z "$SYNCTHING_ANDROID_PREBUILT" ] && echo "Prebuild disabled" && exit 0

for ARCH in arm x86 arm64 x86_64; do
  GOARCH=${ARCH}
  SDK=16
  # The values here must correspond with those in ../syncthing/build-syncthing.py
  case ${ARCH} in
      arm)
        GCC="arm-linux-androideabi-clang"
        ;;
      arm64)
        SDK=21
        GCC="aarch64-linux-android-clang"
        ;;
      x86)
        GOARCH=386
        GCC="i686-linux-android-clang"
        ;;
      x86_64)
        SDK=21
        GOARCH=amd64
        GCC="x86_64-linux-android21-clang"
        ;;
      *)
        echo "Invalid architecture"
        exit 1
  esac

  STANDALONE_NDK_DIR="${ANDROID_NDK_HOME}/standalone-ndk/android-${SDK}-${GOARCH}"
  echo "Building standalone NDK - ${STANDALONE_NDK_DIR}"
  ${ANDROID_NDK_HOME}/build/tools/make-standalone-toolchain.sh \
    --platform=android-${SDK} --arch=${ARCH} \
    --install-dir=${STANDALONE_NDK_DIR}

   echo "Pre-building Go standard library for $GOARCH"
   CGO_ENABLED=1 CC="${STANDALONE_NDK_DIR}/bin/${GCC}" \
      GOOS=android GOARCH=$GOARCH go install -v std
done

echo "Prepopulating gradle and go build/pkg cache"
git clone https://github.com/syncthing/syncthing-android
cd syncthing-android
./gradlew --no-daemon lint
cd ..
rm -rf syncthing-android
