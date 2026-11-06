#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p app/build/core-tests
javac -encoding UTF-8 -d app/build/core-tests app/src/main/java/org/grimmoir/lunera/lite/Lyrics.java app/src/main/java/org/grimmoir/lunera/lite/PlaybackRules.java tests/CoreChecks.java
java -cp app/build/core-tests org.grimmoir.lunera.lite.CoreChecks
