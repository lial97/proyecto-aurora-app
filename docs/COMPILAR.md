# Compilar Aurora

## Windows (.exe)

El instalador de Windows **solo se puede generar en Windows** (lo hace `jpackage`, la herramienta de Java).

1. Copia a tu PC con Windows el archivo `dist/aurora-codigo.zip` y descomprímelo.
2. Instala **Java 21** (Temurin): <https://adoptium.net/temurin/releases/?version=21>
   — durante la instalación marca **"Set JAVA_HOME"**.
3. Instala **VLC de 64 bits**: <https://www.videolan.org/vlc/> (Aurora lo usa para el audio y el video).
4. Haz doble clic en **`build-windows.bat`**. La primera vez tarda unos minutos (descarga Gradle y WiX).
5. Resultado:
   - **Portable**: `composeApp\build\compose\binaries\main\app\Aurora\Aurora.exe` (no se instala; copia la carpeta entera).
   - **Instalador**: `composeApp\build\compose\binaries\main\exe\Aurora-0.3.0.exe` (crea accesos en el escritorio
     y el menú Inicio).

Comandos equivalentes en una terminal:
```bat
gradlew.bat :composeApp:createDistributable
gradlew.bat :composeApp:packageExe
gradlew.bat :composeApp:packageMsi
```

## Android (.apk)

Se necesita el SDK de Android (las herramientas nuevas ya no piden aceptar licencias aparte):
```sh
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/Android/Sdk "platform-tools" "build-tools;36.0.0"
~/Android/Sdk/cmdline-tools/latest/bin/android --sdk=$HOME/Android/Sdk sdk install "platforms/android-37.0"
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :androidApp:assembleDebug
```
El APK queda en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Para instalarlo en el celular:
- **Con cable**: activa *Opciones de desarrollador → Depuración USB* y ejecuta
  `~/Android/Sdk/platform-tools/adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- **Sin cable**: copia el APK al celular y ábrelo (Android pedirá permitir "instalar apps desconocidas").

## Linux

```sh
./gradlew :composeApp:run                    # probar
./gradlew :composeApp:packageDeb             # paquete .deb
./gradlew :composeApp:createDistributable    # carpeta portable
```
