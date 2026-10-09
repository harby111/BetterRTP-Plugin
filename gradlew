#!/bin/sh
# Minimal wrapper bootstrap. If gradle/wrapper/gradle-wrapper.jar is missing, run `gradle wrapper` once
# (or use an installed Gradle 9+) to regenerate the full wrapper.
APP_HOME=$(cd "$(dirname "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "gradle-wrapper.jar is missing. Install Gradle 9+ and run: gradle wrapper --gradle-version 9.1.0" >&2
  exit 1
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
