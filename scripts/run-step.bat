@echo off
REM Run a single step via the orchestrator API
REM Usage: run-step.bat <step-id>

set ORCHESTRATOR_URL=%ORCHESTRATOR_URL%
if "%ORCHESTRATOR_URL%"=="" set ORCHESTRATOR_URL=http://localhost:8080
set ORCHESTRATOR_USER=%ORCHESTRATOR_USER%
if "%ORCHESTRATOR_USER%"=="" set ORCHESTRATOR_USER=admin
set ORCHESTRATOR_PASS=%ORCHESTRATOR_PASS%
if "%ORCHESTRATOR_PASS%"=="" set ORCHESTRATOR_PASS=changeme

if "%~1"=="" (
    echo Usage: run-step.bat ^<step-id^>
    exit /b 1
)

set STEP_ID=%~1

REM Login
echo [auth] Logging in...
for /f "delims=" %%i in ('powershell -Command ^
    "$r=Invoke-RestMethod -Uri '%ORCHESTRATOR_URL%/api/auth/login' -Method Post -ContentType 'application/json' -Body '{\"username\":\"%ORCHESTRATOR_USER%\",\"password\":\"%ORCHESTRATOR_PASS%\"}'; $r.data.token"') do set JWT_TOKEN=%%i

if "%JWT_TOKEN%"=="" (
    echo [auth] Login failed
    exit /b 1
)

echo [auth] Logged in successfully

REM Trigger the step
echo [run-step] Triggering step #%STEP_ID%...
for /f "delims=" %%i in ('powershell -Command ^
    "$r=Invoke-RestMethod -Uri '%ORCHESTRATOR_URL%/api/steps/%STEP_ID%/run' -Method Post -Headers @{Authorization='Bearer %JWT_TOKEN%'} -ContentType 'application/json'; $r.data.runId"') do set RUN_ID=%%i

if "%RUN_ID%"=="" (
    echo [run-step] Failed to trigger step
    exit /b 1
)

echo [run-step] Run #%RUN_ID% created, waiting for completion...

REM Poll until complete
set MAX_WAIT=300
set ELAPSED=0
:poll_loop
if %ELAPSED% GEQ %MAX_WAIT% (
    echo [poll] Timed out waiting for run #%RUN_ID%
    exit /b 1
)

for /f "delims=" %%i in ('powershell -Command ^
    "$r=Invoke-RestMethod -Uri '%ORCHESTRATOR_URL%/api/runs/%RUN_ID%' -Method Get -Headers @{Authorization='Bearer %JWT_TOKEN%'}; $r.data.status"') do set RUN_STATUS=%%i

if "%RUN_STATUS%"=="SUCCESS" goto :done
if "%RUN_STATUS%"=="FAILED" goto :done
if "%RUN_STATUS%"=="PARTIAL" goto :done
if "%RUN_STATUS%"=="CANCELLED" goto :done

timeout /t 3 /nobreak >nul
set /a ELAPSED+=3
goto :poll_loop

:done
echo [run-step] Step #%STEP_ID% finished — Status: %RUN_STATUS%

if "%RUN_STATUS%"=="SUCCESS" exit /b 0
exit /b 1
