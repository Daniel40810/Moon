@echo off
REM =====================================================================
REM FMoon  job.bat
REM Der Schalter fuer die naechtliche Pflege FMN_NIGHTLY_JOB.
REM
REM   job.bat          zeigt den Stand, aendert nichts
REM   job.bat an       schaltet ein und laesst gleich einen Lauf durch
REM   job.bat aus      schaltet aus (der Job bleibt angelegt)
REM   job.bat jetzt    ein Lauf von Hand, egal ob ein- oder ausgeschaltet
REM
REM ACHTUNG: db_setup.bat legt den Job neu an und laesst ihn dabei
REM AUSGESCHALTET. Nach einem Neuaufbau also wieder job.bat an.
REM
REM Das Protokoll landet in build\job.log.
REM =====================================================================
setlocal
cd /d "%~dp0"
if not exist build mkdir build

set VERB=%~1
if "%VERB%"=="" set VERB=status

if /I "%VERB%"=="an"     set SKRIPT=db\job_an.sql
if /I "%VERB%"=="aus"    set SKRIPT=db\job_aus.sql
if /I "%VERB%"=="jetzt"  set SKRIPT=db\job_jetzt.sql
if /I "%VERB%"=="status" set SKRIPT=db\job_status.sql

if not defined SKRIPT (
  echo Unbekannt: %VERB%
  echo Aufruf: job.bat [an^|aus^|jetzt^|status]
  goto ende
)

echo FMoon Job-Schalter: %VERB%
echo.
sqlplus -S demo/de@//localhost:1521/PDBORCL @%SKRIPT% > build\job.log 2>&1
type build\job.log

echo.
echo Protokoll: build\job.log
findstr /C:"ORA-" /C:"SP2-" build\job.log
:ende
endlocal
