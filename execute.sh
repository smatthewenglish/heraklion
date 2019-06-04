#!/usr/bin/env bash
./gradlew clean && ./gradlew fatJar && java -jar ./build/libs/server-all-1.0-SNAPSHOT.jar