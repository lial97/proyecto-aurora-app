@echo off
setlocal
title Aurora - compilar para Windows
cd /d "%~dp0"
echo.
echo  ===  Aurora: compilar para Windows  ===
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo  [X] No se encontro Java.
  echo      Instala Java 21 ^(Temurin^): https://adoptium.net/temurin/releases/?version=21
  echo      Marca la opcion "Set JAVA_HOME" durante la instalacion y vuelve a ejecutar este archivo.
  pause
  exit /b 1
)
java -version 2>&1 | findstr /c:"version \"21" >nul
if errorlevel 1 (
  echo  [!] Se necesita Java 21. Esta es la version instalada:
  java -version
  echo      Instala Temurin 21: https://adoptium.net/temurin/releases/?version=21
  pause
  exit /b 1
)

echo  [1/3] Preparando VLC ^(va dentro del instalador: no hay que instalarlo aparte^)...
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\windows\preparar-vlc.ps1" -Out "composeApp\resources\windows\vlc"
if errorlevel 1 goto error

echo.
echo  [2/3] Version portable ^(carpeta con Aurora.exe, sin instalar^)...
call gradlew.bat :composeApp:createReleaseDistributable --console=plain
if errorlevel 1 goto error

echo.
echo  [3/3] Instalador .exe ^(descarga WiX automaticamente la primera vez^)...
call gradlew.bat :composeApp:packageReleaseExe --console=plain
if errorlevel 1 goto error

if not exist dist mkdir dist
copy /y "composeApp\build\compose\binaries\main-release\exe\*.exe" dist\ >nul

echo.
echo  ===  Listo  ===
echo  Instalador: dist\  ^(Aurora-^<version^>.exe: siguiente, siguiente y listo; VLC ya va adentro^)
echo  Portable:   composeApp\build\compose\binaries\main-release\app\Aurora\Aurora.exe
echo.
explorer dist
pause
exit /b 0

:error
echo.
echo  [X] La compilacion fallo. Copia el mensaje de error de arriba para revisarlo.
pause
exit /b 1
