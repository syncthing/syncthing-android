#!/bin/bash

set -e

LATEST_TAG=$1

echo "

Checking for Syncthing Update
-----------------------------
"
PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"
cd "syncthing/src/github.com/syncthing/syncthing/"
git fetch
CURRENT_TAG=$(git describe)
if [ -z "$LATEST_TAG" ]; then
    LATEST_TAG=$(git tag --sort=taggerdate | tail -1)
fi

if [ ${CURRENT_TAG} != ${LATEST_TAG} ]; then
    git checkout -f ${LATEST_TAG}
    cd ${PROJECT_DIR}
    git add "syncthing/src/github.com/syncthing/syncthing/"
    git commit -m "Updated Syncthing to $LATEST_TAG"
else
    echo "Syncthing up-to-date at $CURRENT_TAG"
fi
cd ${PROJECT_DIR}
