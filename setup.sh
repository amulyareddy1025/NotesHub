#!/bin/sh
# One-time setup for Linux / macOS / Git Bash:
#  1) downloads gradle/wrapper/gradle-wrapper.jar
#  2) creates local.properties pointing at your Android SDK
set -e
cd "$(dirname "$0")"

JAR=gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$JAR" ]; then
  echo "Downloading gradle-wrapper.jar ..."
  curl -fL -o "$JAR" https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar
fi

if [ ! -f local.properties ]; then
  SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
  if [ -n "$SDK" ]; then
    echo "sdk.dir=$SDK" > local.properties
    echo "Created local.properties (sdk.dir=$SDK)"
  else
    echo "WARNING: ANDROID_HOME is not set. Set it, or create local.properties with sdk.dir=<path>"
  fi
fi

chmod +x gradlew
echo "Setup complete. Now run: ./gradlew assembleDebug"
