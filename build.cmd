@echo off
REM ===========================================================================
REM  Gauge Typed Parameters - build
REM
REM  Compiles against the REAL IntelliJ IDEA 2026.2.1 / build 262 platform.
REM  JetBrains requires JDK 25 for IntelliJ Platform 2026.2 plugin development
REM  (https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html).
REM
REM  This script uses a project-local / explicitly located JDK 25 for THIS BUILD
REM  ONLY. Everything below runs inside setlocal, so JAVA_HOME and PATH are
REM  restored when the script exits - the machine's Java 17 install, the global
REM  JAVA_HOME and the system default java are never touched.
REM ===========================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "LOG=%~dp0build-log.txt"
echo === Gauge Typed Parameters build === > "%LOG%"
echo DATE: %DATE% %TIME% >> "%LOG%"

REM --- locate a JDK 25 -------------------------------------------------------
set "JDK25="

REM 1) project-local JDK, highest priority:  <project>\jdk
if exist "%~dp0jdk\bin\java.exe" (
  call :checkJdk25 "%~dp0jdk"
)

REM 2) explicit override:  set JDK25_HOME=... before running this script
if not defined JDK25 if defined JDK25_HOME (
  if exist "%JDK25_HOME%\bin\java.exe" call :checkJdk25 "%JDK25_HOME%"
)

REM 3) IntelliJ's JDK folder
if not defined JDK25 call :scanRoot "%USERPROFILE%\.jdks"

REM 4) common Windows install locations
if not defined JDK25 call :scanRoot "C:\Program Files\Eclipse Adoptium"
if not defined JDK25 call :scanRoot "C:\Program Files\Java"
if not defined JDK25 call :scanRoot "C:\Program Files\Microsoft"
if not defined JDK25 call :scanRoot "C:\Program Files\Amazon Corretto"
if not defined JDK25 call :scanRoot "C:\Program Files\Zulu"
if not defined JDK25 call :scanRoot "C:\Program Files\JetBrains\jbr"
if not defined JDK25 call :scanRoot "%LOCALAPPDATA%\Programs\Eclipse Adoptium"

if not defined JDK25 goto noJdk25

echo Using JDK 25: %JDK25% >> "%LOG%"
echo Using JDK 25: %JDK25%

REM --- scoped to this script only --------------------------------------------
set "JAVA_HOME=%JDK25%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

"%JAVA_HOME%\bin\java.exe" -version >> "%LOG%" 2>&1

echo. >> "%LOG%"
echo ---- gradlew clean test buildPlugin (platform 2026.2.1 / build 262) ---- >> "%LOG%"
call "%~dp0gradlew.bat" --no-daemon --console=plain --stacktrace clean test buildPlugin >> "%LOG%" 2>&1
set EC=%ERRORLEVEL%
echo. >> "%LOG%"
echo EXITCODE=%EC% >> "%LOG%"

echo. >> "%LOG%"
echo ---- distributions ---- >> "%LOG%"
dir /b /s "%~dp0build\distributions" >> "%LOG%" 2>&1

type "%LOG%"
echo.
echo Log written to %LOG%
endlocal
exit /b 0

REM --- helper: scan every immediate subdirectory of %1 ------------------------
:scanRoot
if defined JDK25 goto :eof
if not exist "%~1" goto :eof
for /d %%d in ("%~1\*") do (
  if not defined JDK25 if exist "%%~d\bin\java.exe" call :checkJdk25 "%%~d"
)
goto :eof

REM --- helper: set JDK25 if %1 is a JDK 25 -----------------------------------
:checkJdk25
if defined JDK25 goto :eof
set "CAND=%~1"
if not exist "%CAND%\release" goto :eof
findstr /b /c:"JAVA_VERSION=\"25" "%CAND%\release" >NUL 2>&1
if errorlevel 1 goto :eof
set "JDK25=%CAND%"
goto :eof

:noJdk25
echo. >> "%LOG%"
echo NO JDK 25 FOUND - build not started. >> "%LOG%"
type "%LOG%"
echo.
echo ===========================================================================
echo  No JDK 25 was found, so the build was NOT started.
echo.
echo  IntelliJ Platform 2026.2 requires JDK 25 to compile against. Your machine
echo  Any existing Java installation on this machine is left untouched.
echo.
echo  Download Eclipse Temurin 25 (LTS), Windows x64, JDK, .zip:
echo    https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse
echo    (currently jdk-25.0.4.1+1 - OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip)
echo.
echo  Extract it so that java.exe ends up at ONE of these paths:
echo    %~dp0jdk\bin\java.exe                        ^(project-local, preferred^)
echo    %USERPROFILE%\.jdks\jdk-25.0.4.1\bin\java.exe ^(IntelliJ's JDK folder^)
echo.
echo  Then just run build.cmd again. Use the .zip, not the .msi - the zip does
echo  not register itself as the system Java and cannot disturb your Java 17.
echo.
echo  Alternatively:  set JDK25_HOME=D:\path\to\jdk-25  then run build.cmd
echo ===========================================================================
endlocal
exit /b 1
