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

echo  [1/2] Version portable ^(carpeta con Aurora.exe, sin instalar^)...
call gradlew.bat :composeApp:createDistributable --console=plain
if errorlevel 1 goto error

echo.
echo  [2/2] Instalador .exe ^(descarga WiX automaticamente la primera vez^)...
call gradlew.bat :composeApp:packageExe --console=plain
if errorlevel 1 goto error

echo.
echo  ===  Listo  ===
echo  Portable:   composeApp\build\compose\binaries\main\app\Aurora\Aurora.exe
echo  Instalador: composeApp\build\compose\binaries\main\exe\
echo.
echo  Recuerda: para escuchar musica y ver videos instala VLC de 64 bits: https://www.videolan.org/vlc/
explorer "composeApp\build\compose\binaries\main\exe"
pause
exit /b 0

:error
echo.
echo  [X] La compilacion fallo. Copia el mensaje de error de arriba para revisarlo.
pause
exit /b 1
