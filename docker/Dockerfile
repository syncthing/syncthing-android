FROM ubuntu:16.04

RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get -y install git openjdk-8-jdk wget unzip gcc

RUN useradd -m build
WORKDIR /home/build
USER build

RUN wget http://dl.google.com/android/repository/android-ndk-r12-linux-x86_64.zip \
        && unzip android-ndk-r12-linux-x86_64.zip \
        && rm android-ndk-r12-linux-x86_64.zip

RUN git clone https://github.com/syncthing/syncthing-android.git
WORKDIR syncthing-android

RUN git submodule update --init ext/golang/go1.4
RUN git submodule update --init ext/golang/go
RUN git submodule update --init ext/syncthing

RUN ./make-all.bash
ENV ANDROID_NDK /home/build/android-ndk-r12

RUN cd .. \
    && wget https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz \
    && tar -xzf android-sdk_r24.4.1-linux.tgz \
    && rm android-sdk_r24.4.1-linux.tgz

ENV ANDROID_HOME /home/build/android-sdk-linux

# from https://github.com/bitrise-docker/android/blob/master/Dockerfile

ENV PATH ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools
# ------------------------------------------------------
# --- Install Android SDKs and other build packages

# Other tools and resources of Android SDK
#  you should only install the packages you need!
# To get a full list of available options you can use:
#  android list sdk --no-ui --all --extended
# (!!!) Only install one package at a time, as "echo y" will only work for one license!
#       If you don't do it this way you might get "Unknown response" in the logs,
#         but the android SDK tool **won't** fail, it'll just **NOT** install the package.
RUN echo y | android update sdk --no-ui --all --filter platform-tools | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter extra-android-support | grep 'package installed'

# SDKs
# Please keep these in descending order!
RUN echo y | android update sdk --no-ui --all --filter android-24 | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter android-23 | grep 'package installed'

# build tools
# Please keep these in descending order!
RUN echo y | android update sdk --no-ui --all --filter build-tools-24.0.0 | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter build-tools-23.0.3 | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter build-tools-23.0.2 | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter build-tools-23.0.1 | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter build-tools-22.0.1 | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter build-tools-21.1.2 | grep 'package installed'

# Android System Images, for emulators
# Please keep these in descending order!
RUN echo y | android update sdk --no-ui --all --filter sys-img-armeabi-v7a-android-23 | grep 'package installed'

# Extras
RUN echo y | android update sdk --no-ui --all --filter extra-android-m2repository | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter extra-google-m2repository | grep 'package installed'
RUN echo y | android update sdk --no-ui --all --filter extra-google-google_play_services | grep 'package installed'

# google apis
# Please keep these in descending order!
RUN echo y | android update sdk --no-ui --all --filter addon-google_apis-google-23 | grep 'package installed'

RUN ./gradlew clean
#RUN ./gradlew build
RUN ./gradlew test
#RUN ./gradlew lint
#RUN ./gradlew tasks

