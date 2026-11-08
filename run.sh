#!/usr/bin/env bash
# Compiles StudyMuse with the JDK (17+) and runs it.
#   ./run.sh        -> GUI
#   ./run.sh cli    -> interactive command line
# On first run it downloads Apache PDFBox into lib/ for PDF uploads (optional; .txt/.md work without it).
set -e
cd "$(dirname "$0")"
PDFBOX_VERSION=3.0.3
PDFBOX_JAR="lib/pdfbox-app-$PDFBOX_VERSION.jar"
if [ ! -f "$PDFBOX_JAR" ]; then
  mkdir -p lib
  echo "Downloading PDFBox $PDFBOX_VERSION for PDF support (one time) ..."
  if ! curl -fsSL -o "$PDFBOX_JAR.part" \
      "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/$PDFBOX_VERSION/pdfbox-app-$PDFBOX_VERSION.jar"; then
    rm -f "$PDFBOX_JAR.part"
    echo "Could not download PDFBox - continuing without PDF support."
  else
    mv "$PDFBOX_JAR.part" "$PDFBOX_JAR"
  fi
fi
rm -rf out && mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name "*.java")
java -cp "out:lib/*" com.studymuse.Main "$@"
