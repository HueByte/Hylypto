#!/bin/bash
# Clean build artifacts
cd "$(dirname "$0")/.."
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
./gradlew clean "$@"
