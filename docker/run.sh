#!/bin/bash -e

GIT_REPO=${GIT_REPO:-https://github.com/syncthing/syncthing-android}
GIT_BRANCH=${GIT_BRANCH:-master}
git clone $GIT_REPO
cd ${GIT_REPO##*/}
git fetch origin $GIT_BRANCH
git checkout $GIT_BRANCH
git submodule init
git submodule update

if [ ! -z "$BUILD_DIR" ] && [ -d "$BUILD_DIR" ]; then
  echo "Mounting build directory"
  ln -s $BUILD_DIR app/build
fi

exec $@

