# Compilar Aurora

## Windows (.exe)

El instalador de Windows **solo se puede generar en Windows** (lo hace `jpackage`, la herramienta de Java).

1. Baja el código con Git (o actualízalo si ya lo tienes):
   ```bat
   git clone https://github.com/lial97/proyecto-aurora-app.git
   cd proyecto-aurora-app
   git pull
   ```
2. Instala **Java 21** (Temurin): <https://adoptium.net/temurin/releases/?version=21>
   — durante la instalación marca **"Set JAVA_HOME"**.
3. Haz doble clic en **`build-windows.bat`**. No hace falta instalar VLC: el script descarga VLC 3.0.24 portable
   (comprobando su huella SHA-256) y lo mete dentro del instalador. La primera vez tarda unos minutos
   (descarga Gradle, VLC y WiX).
4. Resultado:
   - **Instalador**: `AURORA-APP\Aurora-0.3.0.exe` (junto al APK; crea accesos en el escritorio y el menú Inicio). Al desinstalarlo se borran la configuración y la caché, así que reinstalar deja la app como nueva; actualizar a otra versión las conserva. La plantilla del instalador es `composeApp\windows\main.wxs`.
   - **Portable**: `composeApp\build\compose\binaries\main-release\app\Aurora\Aurora.exe` (no se instala; copia la carpeta entera).

Comandos equivalentes en una terminal:
```bat
gradlew.bat :composeApp:createReleaseDistributable
gradlew.bat :composeApp:packageWindowsExe
```

Para probar sin instalar (modo de depuración, con "Repetir configuración inicial" en Ajustes › Acerca de):
```bat
gradlew.bat :composeApp:run
```

## Android (.apk)

Se necesita el SDK de Android (las herramientas nuevas ya no piden aceptar licencias aparte):
```sh
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/Android/Sdk "platform-tools" "build-tools;36.0.0"
~/Android/Sdk/cmdline-tools/latest/bin/android --sdk=$HOME/Android/Sdk sdk install "platforms/android-37.0"
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :androidApp:assembleRelease
```
El APK queda en `androidApp/build/outputs/apk/release/androidApp-release.apk` (~6 MB). Es la versión optimizada con
R8: más fluida y con menos memoria que la de depuración (`assembleDebug`, ~23 MB), que solo sirve para depurar.
Va firmada con la clave de depuración del equipo, así que se instala encima de una de depuración sin perder datos.

Para instalarlo en el celular:
- **Con cable**: activa *Opciones de desarrollador → Depuración USB* y ejecuta
  `~/Android/Sdk/platform-tools/adb install -r androidApp/build/outputs/apk/release/androidApp-release.apk`
- **Sin cable**: copia el APK al celular y ábrelo (Android pedirá permitir "instalar apps desconocidas").

## Linux

```sh
./gradlew :composeApp:run                    # probar (modo de depuración)
bash scripts/empaquetar-linux.sh             # .deb y AppImage con VLC adentro (Docker) → AURORA-APP/
```
