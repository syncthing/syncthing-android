#!/bin/bash -e

# Build the syncthing library
./make-syncthing.bash 386
./make-syncthing.bash arm
./make-syncthing.bash arm64
