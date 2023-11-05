#!/usr/bin/env bash

set -e

printf "Pulling Translations
-----------------------------
"
# Force pull to make sure this is executed. Apparently tx only compares timestamps, not file
# contents. So if a file was `touch`ed, it won't be updated by default.
# Use multiple transifex instances for pulling to speed things up.
tx pull -a -f "syncthing-android.stringsxml" &
tx pull -a -f "syncthing-android.description_fulltxt" &
tx pull -a -f "syncthing-android.description_shorttxt" &
tx pull -a -f "syncthing-android.titletxt" &
wait
./gradlew deleteUnsupportedPlayTranslations
git add -A "app/src/main/play/"
git add -A "app/src/main/res/values-*/strings.xml"
if ! git diff --cached --exit-code >/dev/null;
then
    git commit -m "Imported translations"
fi
