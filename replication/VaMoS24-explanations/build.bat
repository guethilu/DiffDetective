@echo off
setlocal

set "targetSubPath=VaMoS24-explanations"

rem Get the current directory
for %%A in ("%CD%") do set "currentDir=%%~nxA"

rem Check if the current directory ends with the target sub-path
if "%currentDir:~-20%"=="%targetSubPath%" (
    cd ..\..
    docker build -t diff-detective -f replication\VaMoS24-explanations\Dockerfile .
    @pause
) else (
    echo error: the script must be run from inside the VaMoS24-explanations directory, i.e., DiffDetective\replication\%targetSubPath%
)
endlocal
