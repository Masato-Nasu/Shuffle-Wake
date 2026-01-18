#!/usr/bin/env sh

# Minimal Gradle wrapper script.
# It relies on gradle/wrapper/gradle-wrapper.jar (included in this project).

DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_CMD="java"

exec "$JAVA_CMD" -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
