#!/bin/bash
./lib/gradle/gradlew buildNative $@
./lib/gradle/gradlew assembleDebug $@
