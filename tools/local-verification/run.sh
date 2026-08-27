#!/usr/bin/env bash
# Compiles and runs the plugin's pure logic against a stub IntelliJ/Gauge API tree.
# Needs only a JDK and kotlinc on PATH - no IntelliJ Platform download, no Gradle.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HERE/../../src/main/kotlin"
OUT="$HERE/.out"
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/stubs"

find "$HERE/stubs" -name '*.java' > "$OUT/javafiles.txt"
javac -nowarn -d "$OUT/stubs" @"$OUT/javafiles.txt"
kotlinc -d "$OUT/classes" \
  $(cat "$OUT/javafiles.txt" | tr '\n' ' ') \
  "$HERE/kstubs/com/intellij/openapi/components/Services.kt" \
  "$HERE/LocalChecks.kt" \
  $(find "$SRC" -name '*.kt' | tr '\n' ' ')

KOTLIN_STDLIB="$(dirname "$(readlink -f "$(command -v kotlinc)")")/../lib/kotlin-stdlib.jar"
java -cp "$OUT/classes:$OUT/stubs:$KOTLIN_STDLIB" localchecks.LocalChecksKt
