#!/bin/sh
# Minimal Gradle wrapper launcher (Linux / macOS / Git Bash).
# Needs gradle/wrapper/gradle-wrapper.jar -> run ./setup.sh once to download it.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar is missing. Run ./setup.sh (or setup.bat on Windows) first." >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD=java
fi

exec "$JAVACMD" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew \
  -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
