@echo off
REM Compiles StudyMuse with the JDK (17+) and runs it.
REM   run.bat        -> GUI
REM   run.bat cli    -> interactive command line
REM On first run it downloads Apache PDFBox into lib\ for PDF uploads (optional; .txt/.md work without it).
cd /d %~dp0
set PDFBOX_VERSION=3.0.3
set PDFBOX_JAR=lib\pdfbox-app-%PDFBOX_VERSION%.jar
if not exist "%PDFBOX_JAR%" (
  if not exist lib mkdir lib
  echo Downloading PDFBox %PDFBOX_VERSION% for PDF support ^(one time^) ...
  curl -fsSL -o "%PDFBOX_JAR%.part" "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/%PDFBOX_VERSION%/pdfbox-app-%PDFBOX_VERSION%.jar"
  if errorlevel 1 (
    if exist "%PDFBOX_JAR%.part" del "%PDFBOX_JAR%.part"
    echo Could not download PDFBox - continuing without PDF support.
  ) else (
    move /y "%PDFBOX_JAR%.part" "%PDFBOX_JAR%" > nul
  )
)
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac --release 17 -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
java -cp "out;lib\*" com.studymuse.Main %*
