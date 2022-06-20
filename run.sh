#!/usr/bin/env sh

./gradlew :installDist > /dev/null
build/install/doc_dist/bin/doc_dist "$@"
