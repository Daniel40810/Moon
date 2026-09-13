@echo off
REM Baut das FMN-Schema in DEMO auf.
REM
REM Ohne Argumente wird die Standardliste eingespielt. Einzelne Skripte:
REM   db_setup.bat 08_views.sql 09_scheduler.sql
REM
REM Das Protokoll landet in build\db_setup.log - das ist der Rueckkanal.
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
if not exist build\tools mkdir build\tools

echo Uebersetze...
dir /s /b src\*.java > build\quellen.txt
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp lib\FStyle.jar -d build\classes @build\quellen.txt
if errorlevel 1 goto ende
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp build\classes;lib\FStyle.jar -d build\tools tools\FMoonDbSetup.java tools\FMoonDbCheck.java
if errorlevel 1 goto ende

echo Spiele ein...
java -cp build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar FMoonDbSetup %*

echo.
echo Protokoll: build\db_setup.log
findstr /C:"FEHLER" /C:"Fehler:" /C:"UNGUELTIG" /C:"durchgelaufen" build\db_setup.log
:ende
endlocal
