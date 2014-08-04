#!/bin/bash
gradlew buildNative $@
gradlew assembleDebug $@
