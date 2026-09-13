@echo off
REM Fuellt das FMN-Schema aus dem Java-Rechenkern und prueft beide
REM Seiten gegeneinander. Das Protokoll landet in build\db_check.log.
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
if not exist build\tools mkdir build\tools

dir /s /b src\*.java > build\quellen.txt
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp lib\FStyle.jar -d build\classes @build\quellen.txt
if errorlevel 1 goto ende
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp build\classes;lib\FStyle.jar -d build\tools tools\FMoonDbSetup.java tools\FMoonDbCheck.java
if errorlevel 1 goto ende

java -cp build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar FMoonDbCheck

echo.
echo Protokoll: build\db_check.log
findstr /C:"Ergebnis" /C:"[FEHL]" /C:"ABBRUCH" build\db_check.log
:ende
endlocal
