@echo off
rem Builds build\distributions\psiche-windows.zip: the psiche folder with psiche.exe and the Java runtime it brings,
rem which runs once unzipped anywhere. Run it on Windows: jpackage only packages for the system it runs on.
rem
rem Usage: build_exe.bat
rem
rem Gradle needs Java 17 or later to start (JAVA_HOME, or java on the PATH); it downloads the JDK 25 the app is built
rem with if that is missing.
setlocal

cd /d "%~dp0"

rem build
call gradlew.bat portableZip
if errorlevel 1 exit /b %errorlevel%

echo Built %CD%\build\distributions\psiche-windows.zip
echo The app unzipped is in %CD%\build\package\image\psiche
