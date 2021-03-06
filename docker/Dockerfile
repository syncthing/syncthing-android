FROM openjdk:11

ENV GO_VERSION 1.16.5

ARG ANDROID_SDK_VERSION=7302050
ARG ANDROID_SDK_FILENAME=commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip

WORKDIR /opt

# Install Go
RUN wget -q https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz && \
    tar -zxf go${GO_VERSION}.linux-amd64.tar.gz && \
    rm go${GO_VERSION}.linux-amd64.tar.gz
ENV GOROOT /opt/go
ENV PATH /opt/go/bin:${PATH}

# Install Android SDK manager
RUN mkdir -p /opt/android-sdk && cd /opt/android-sdk && \
    wget -q https://dl.google.com/android/repository/${ANDROID_SDK_FILENAME} && \
    unzip -q ${ANDROID_SDK_FILENAME} && \
    rm ${ANDROID_SDK_FILENAME}
ENV ANDROID_HOME /opt/android-sdk

ARG SDKMANAGER="${ANDROID_HOME}/cmdline-tools/bin/sdkmanager --sdk_root=${ANDROID_HOME}"

# Accept the SDK license, as we can't install packages otherwise
RUN yes | $SDKMANAGER --licenses > /dev/null

# NDK version (r22 fails to build)
ENV NDK_VERSION 21.4.7075529

# Install other android packages, including NDK
RUN $SDKMANAGER tools platform-tools "build-tools;29.0.3" "platforms;android-29" "extras;android;m2repository" "ndk;${NDK_VERSION}"

# Accept licenses of newly installed packages
RUN yes | $SDKMANAGER --licenses

# Setup the NDK path
ENV ANDROID_NDK_HOME ${ANDROID_HOME}/ndk/${NDK_VERSION}

# Enable prebuild mode
ENV SYNCTHING_ANDROID_PREBUILT 1

# Set location of go cache
ENV GOCACHE /opt/gocache

# Set location of GOPATH to persist packages for module builds in GOPATH/pkg/mod
ENV GOPATH /opt/gopath

# Run prebuild script (will prebuild stuff into the image if env var is set)
ADD prebuild.sh /opt/prebuild.sh
RUN /opt/prebuild.sh

WORKDIR /mnt
