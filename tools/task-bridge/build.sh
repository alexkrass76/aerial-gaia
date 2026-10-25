#!/bin/sh
set -eu
: "ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
: "JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
bridge_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
bridge_out=${1:?Pass an output directory outside tracked sources}
mkdir -p "$bridge_out/classes" "$bridge_out/dex"
"$JAVA_HOME/bin/javac" -source 8 -target 8 \
  -cp "$ANDROID_HOME/platforms/android-36/android.jar" \
  -d "$bridge_out/classes" "$bridge_dir"/src/gaia/bridge/*.java "$bridge_dir"/policy/gaia/bridge/*.java
"$ANDROID_HOME/build-tools/36.0.0/d8" \
  --lib "$ANDROID_HOME/platforms/android-36/android.jar" \
  --output "$bridge_out/dex" "$bridge_out"/classes/gaia/bridge/*.class
printf 'Helper dex: %s/dex/classes.dex\n' "$bridge_out"
