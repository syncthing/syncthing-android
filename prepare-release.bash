#!/bin/bash

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
$SCRIPT_DIR/update-syncthing.bash "$2"
$SCRIPT_DIR/check-version.bash "$1"
$SCRIPT_DIR/pull-translations.bash
$SCRIPT_DIR/bump-version.bash "$1"

echo "
Update ready.
"
