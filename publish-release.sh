#!/usr/bin/env bash

set -e

version=$(git describe --always)
regex='^[0-9]+\.[0-9]+\.[0-9]+$'
if [[ ! ${version} =~ $regex ]]
then
    echo "Current commit is not a release"
    exit;
fi

echo "

Pushing to Github
-----------------------------
"
git push
git push --tags

version=`git describe --tags --abbrev=0`

echo "

Enter Changelog for $version
-----------------------------
"
changelog_file="/tmp/changelog.tmp"
touch
${DEFAULT_EDITOR} $changelog_file

changelog=`cat $changelog_file`
rm $changelog_file
echo $changelog > "src/main/play/en-GB/whatsnew"

echo "

Push to Google Play
-----------------------------
"

read -p "Enter signing password: " password

SIGNING_PASSWORD=$password ./gradlew assembleRelease

# Upload apk and listing to Google Play
./gradlew publishRelease

echo "

Release published!
"

#echo "
#
#Create Github Release
#-----------------------------
#"
#ACCESS_TOKEN=""
#api_json=$(printf '{"tag_name": "v%s","target_commitish": "master","name": "v%s","body": "%s","draft": false,"prerelease": false}' $version $version $changelog)
#curl --data "$api_json" https://api.github.com/repos/syncthing/syncthing-android/releases?access_token=$ACCESS_TOKEN
