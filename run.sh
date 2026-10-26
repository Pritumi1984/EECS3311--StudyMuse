#!/usr/bin/env bash
# Compiles StudyMuse with the JDK (17+) and runs it.
#   ./run.sh        -> GUI
#   ./run.sh cli    -> interactive command line
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp out com.studymuse.Main "$@"
