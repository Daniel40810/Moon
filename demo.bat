@echo off
REM Startet die Vorfuehrung.
REM -source 8 -target 8, damit die Klassen auch auf einer aelteren
REM Java-Laufzeit laufen als der javac, der sie uebersetzt hat.
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
dir /s /b src\*.java > build\quellen.txt
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp lib\FStyle.jar -d build\classes @build\quellen.txt
if errorlevel 1 goto ende
java -cp build\classes;lib\FStyle.jar com.dan.fmoon.FMoonDemo
:ende
endlocal
