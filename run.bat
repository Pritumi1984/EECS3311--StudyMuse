@echo off
REM Compiles StudyMuse with the JDK (17+) and runs it.
REM   run.bat        -> GUI
REM   run.bat cli    -> interactive command line
cd /d %~dp0
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac --release 17 -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
java -cp out com.studymuse.Main %*
