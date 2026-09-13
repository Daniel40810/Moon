@echo off
REM Misst die Hafenzeit einer Station aus beobachteten Hochwasserzeiten
REM und schlaegt den Eintrag in die Datenbank vor.
REM
REM   hafenzeit.bat -harmonik
REM   hafenzeit.bat db\hafenzeiten_muster.txt
REM   hafenzeit.bat meine_zeiten.txt -schreiben
REM   hafenzeit.bat kurze_reihe.txt -kurz
REM
REM Ohne -schreiben wird nichts veraendert; das Werkzeug zeigt nur den
REM gemessenen Wert und den fertigen Aufruf zum Eintragen.
setlocal
cd /d "%~dp0"
if "%~1"=="" (
  echo Aufruf: hafenzeit.bat ^<datei^> [-schreiben] [-kurz]
  echo         hafenzeit.bat -harmonik
  echo Ein Muster liegt in db\hafenzeiten_muster.txt.
  goto ende
)
if not exist build\classes mkdir build\classes
if not exist build\tools mkdir build\tools

dir /s /b src\*.java > build\quellen.txt
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp lib\FStyle.jar -d build\classes @build\quellen.txt
if errorlevel 1 goto ende
javac -nowarn -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp build\classes;lib\FStyle.jar;lib\ojdbc11.jar -d build\tools tools\FMoonHarbourFit.java
if errorlevel 1 goto ende

java -cp build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar FMoonHarbourFit %*
:ende
endlocal
