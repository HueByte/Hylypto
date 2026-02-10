#!/bin/bash
# Build the Hylypto plugin JAR
cd "$(dirname "$0")/.."
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
./gradlew build "$@"
