@echo off
REM Shared auth helper for orchestrator CLI scripts (Windows)
REM Usage: call auth.bat login
REM        call auth.bat api_req METHOD PATH
REM        call auth.bat poll_run RUN_ID [MAX_WAIT]

set ORCHESTRATOR_URL=%ORCHESTRATOR_URL%
if "%ORCHESTRATOR_URL%"=="" set ORCHESTRATOR_URL=http://localhost:8080
set ORCHESTRATOR_USER=%ORCHESTRATOR_USER%
if "%ORCHESTRATOR_USER%"=="" set ORCHESTRATOR_USER=admin
set ORCHESTRATOR_PASS=%ORCHESTRATOR_PASS%
if "%ORCHESTRATOR_PASS%"=="" set ORCHESTRATOR_PASS=changeme

if "%~1"=="" (
    echo [auth] Usage: auth.bat {login|api_req|poll_run}
    exit /b 1
)

set ACTION=%~1
shift

if "%ACTION%"=="login" goto :login
if "%ACTION%"=="api_req" goto :api_req
if "%ACTION%"=="poll_run" goto :poll_run

echo [auth] Unknown action: %ACTION%
exit /b 1

:login
if "%JWT_TOKEN%" neq "" (
    echo [auth] Using pre-existing token
    exit /b 0
)

echo [auth] Logging in...
for /f "delims=" %%i in ('powershell -Command ^
    "$r=Invoke-RestMethod -Uri '%ORCHESTRATOR_URL%/api/auth/login' -Method Post -ContentType 'application/json' -Body '{\"username\":\"%ORCHESTRATOR_USER%\",\"password\":\"%ORCHESTRATOR_PASS%\"}'; if($r.status -eq 'SUCCESS') { $r.data.token } else { Write-Error $r.status }"') do set JWT_TOKEN=%%i

if "%JWT_TOKEN%"=="" (
    echo [auth] Login failed
    exit /b 1
)

echo [auth] Logged in successfully
exit /b 0

:api_req
if "%~1"=="" (
    echo [auth] Usage: auth.bat api_req METHOD PATH
    exit /b 1
)

set METHOD=%~1
set PATH_REQ=%~2
powershell -Command ^
    "Invoke-RestMethod -Uri '%ORCHESTRATOR_URL%/%PATH_REQ%' -Method %METHOD% -Headers @{Authorization='Bearer %JWT_TOKEN%'} -ContentType 'application/json'"
exit /b 0

:poll_run
if "%~1"=="" (
    echo [auth] Usage: auth.bat poll_run RUN_ID [MAX_WAIT]
    exit /b 1
)

set RUN_ID=%~1
set MAX_WAIT=%~2
if "%MAX_WAIT%"=="" set MAX_WAIT=300
set ELAPSED=0

echo [poll] Waiting for run #%RUN_ID% to complete...

:poll_loop
if %ELAPSED% GEQ %MAX_WAIT% (
    echo [poll] Timed out waiting for run #%RUN_ID%
    exit /b 1
)

for /f "delims=" %%i in ('powershell -Command ^
    "$r=Invoke-RestMethod -Uri '%ORCHESTRATOR_URL%/api/runs/%RUN_ID%' -Method Get -Headers @{Authorization='Bearer %JWT_TOKEN%'} -ContentType 'application/json'; $r.data.status"') do set RUN_STATUS=%%i

if "%RUN_STATUS%"=="SUCCESS" goto :poll_done
if "%RUN_STATUS%"=="FAILED" goto :poll_done
if "%RUN_STATUS%"=="PARTIAL" goto :poll_done
if "%RUN_STATUS%"=="CANCELLED" goto :poll_done

timeout /t 3 /nobreak >nul
set /a ELAPSED+=3
goto :poll_loop

:poll_done
echo [poll] Run completed with status: %RUN_STATUS%
exit /b 0
