@echo off
setlocal
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
set "VCVARS="
if not exist "%VSWHERE%" goto fallback
"%VSWHERE%" -nologo -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -find VC\Auxiliary\Build\vcvars64.bat > "%TEMP%\plz-vcvars.txt"
set /p VCVARS=<"%TEMP%\plz-vcvars.txt"
del "%TEMP%\plz-vcvars.txt" 2>nul
:fallback
if not defined VCVARS set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
if not exist "%VCVARS%" (
    echo vcvars64.bat not found: %VCVARS%
    exit /b 1
)
call "%VCVARS%" >nul || exit /b 1
set "OUT=%~1"
if "%OUT%"=="" set "OUT=%~dp0..\dist"
if not exist "%OUT%" mkdir "%OUT%"
pushd "%~dp0"
cl /nologo /O2 /GS- /W4 /LD /Brepro plz-native.c /Fo"%OUT%\plz-native.obj" /link /NODEFAULTLIB /ENTRY:DllMain /Brepro /NOIMPLIB /NOEXP kernel32.lib /OUT:"%OUT%\plz-native.dll"
set RC=%ERRORLEVEL%
popd
del "%OUT%\plz-native.obj" 2>nul
exit /b %RC%
