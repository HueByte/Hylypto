#!/bin/bash
# Build and deploy the Hylypto plugin to Hytale mods folder
set -e

cd "$(dirname "$0")/.."

MODS_DIR="$APPDATA/Hytale/UserData/mods"

export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
./gradlew build "$@"

JAR=$(ls build/libs/Hylypto-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: No JAR found in build/libs/"
    exit 1
fi

mkdir -p "$MODS_DIR"
cp "$JAR" "$MODS_DIR/"
echo "Deployed $(basename "$JAR") to $MODS_DIR/"
