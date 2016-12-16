#!/usr/bin/env bash

CHANGELOG_FILE="src/main/play/en-GB/whatsnew"
rm $CHANGELOG_FILE
nano $CHANGELOG_FILE
cat $CHANGELOG_FILE
git push
git push --tags
./gradlew publishRelease
