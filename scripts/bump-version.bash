#!/bin/bash

set -e

if ! git diff-index --exit-code --quiet HEAD; then
    echo "Bumping version aka cutting a release must happen on a clean git workspace"
    exit 1
fi

NEW_VERSION_NAME=$1
OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle.kts" | awk '{print $3}')
if [[ -z ${NEW_VERSION_NAME} ]]
then
    echo "New version name is empty. Please set a new version. Current version: $OLD_VERSION_NAME"
    exit 1
fi

./scripts/check-version.bash "$NEW_VERSION_NAME" 

echo "

Running Lint
-----------------------------
"
./gradlew clean lintVitalRelease

echo "

Enter Changelog for $NEW_VERSION_NAME
-----------------------------
"
CHANGELOG=app/src/main/play/release-notes/en-GB/default.txt
if command -v edit >/dev/null; then
    editor=edit
elif [ -n "$EDITOR" ]; then
    editor="$EDITOR"
else
    echo "No editor found - need either 'edit' binary or EDITOR env var set"
    exit 1
fi
$editor $CHANGELOG

echo "

Updating Version
-----------------------------
"
OLD_VERSION_CODE=$(grep "versionCode" "app/build.gradle.kts" -m 1 | awk '{print $3}')
NEW_VERSION_CODE=$(($OLD_VERSION_CODE + 1))
sed -i -e "s/versionCode = $OLD_VERSION_CODE/versionCode = $NEW_VERSION_CODE/" "app/build.gradle.kts"

OLD_VERSION_NAME=$(grep "versionName" "app/build.gradle.kts" | awk '{print $3}')
sed -i -e "s/$OLD_VERSION_NAME/\"$1\"/" "app/build.gradle.kts"
git add "app/build.gradle.kts" $CHANGELOG
git commit -m "Bumped version to $NEW_VERSION_NAME"
git tag -a ${NEW_VERSION_NAME} -m "
$NEW_VERSION_NAME

$(cat $CHANGELOG)
"
