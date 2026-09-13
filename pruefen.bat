@echo off
REM Baut Quellen und Pruefwerkzeuge und laesst alle Pruefungen laufen.
REM Das Protokoll landet in build\pruefung.log
REM
REM WICHTIG: Auch die Werkzeuge werden mit -source 8 -target 8 uebersetzt.
REM Ohne das erzeugt ein neueres JDK Klassendateien, die eine aeltere
REM Java-Laufzeit nicht laden kann (UnsupportedClassVersionError). Die
REM Versionen von javac und java stehen deshalb oben im Protokoll.
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
if not exist build\tools mkdir build\tools

echo FMoon Pruefung > build\pruefung.log
echo ================================== >> build\pruefung.log
date /t >> build\pruefung.log
time /t >> build\pruefung.log
echo. >> build\pruefung.log
echo --- Werkzeugversionen --- >> build\pruefung.log
javac -version >> build\pruefung.log 2>&1
java -version >> build\pruefung.log 2>&1
echo. >> build\pruefung.log

dir /s /b src\*.java > build\quellen.txt
echo --- Uebersetzung Quellen --- >> build\pruefung.log
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp lib\FStyle.jar -d build\classes @build\quellen.txt >> build\pruefung.log 2>&1
if errorlevel 1 goto fehler

echo --- Uebersetzung Werkzeuge --- >> build\pruefung.log
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp build\classes;lib\FStyle.jar;lib\ojdbc11.jar -d build\tools tools\*.java >> build\pruefung.log 2>&1
if errorlevel 1 goto fehler

echo. >> build\pruefung.log
echo --- Rechenkern --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonAstroTest >> build\pruefung.log 2>&1

echo. >> build\pruefung.log
echo --- Darstellung --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonRenderTest >> build\pruefung.log 2>&1

echo. >> build\pruefung.log
echo --- Gezeiten --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonTideTest >> build\pruefung.log 2>&1

echo. >> build\pruefung.log
echo --- SQL-Skripte --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonSqlLint >> build\pruefung.log 2>&1

echo. >> build\pruefung.log
echo --- Palette --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonMapTest >> build\pruefung.log 2>&1
if errorlevel 1 set FEHLER=1

java -cp build\classes;build\tools;lib\FStyle.jar FMoonOrbitTest >> build\pruefung.log 2>&1
if errorlevel 1 set FEHLER=1

java -cp build\classes;build\tools;lib\FStyle.jar FMoonBeanTest >> build\pruefung.log 2>&1

echo. >> build\pruefung.log
echo --- Bedienleiste --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonDemoTest >> build\pruefung.log 2>&1

echo. >> build\pruefung.log
echo --- Standbilder --- >> build\pruefung.log
java -cp build\classes;build\tools;lib\FStyle.jar FMoonTideShot >> build\pruefung.log 2>&1

echo.
echo Fertig. Protokoll: build\pruefung.log
echo.
findstr /C:"Ergebnis" /C:"FEHL" /C:"Fehler" build\pruefung.log
goto ende

:fehler
echo UEBERSETZUNG FEHLGESCHLAGEN - siehe build\pruefung.log
type build\pruefung.log

:ende
endlocal
