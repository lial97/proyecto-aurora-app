# Cómo continuar el trabajo en Aurora

Este archivo sirve para retomar el trabajo en una conversación nueva. La historia completa de cambios está en
`BITACORA.md` (lo más nuevo arriba) y la estructura del proyecto en `ARQUITECTURA.md`.

## Reglas de trabajo

- Hablar siempre en español. Los textos de la interfaz también van en español.
- Lo específico de Android va en `composeApp/src/androidMain` (y `androidApp/`). No romper escritorio.
- Trabajar por fases: mostrar el resultado de cada una y **esperar la confirmación** del usuario antes de seguir.
- Al terminar cada fase: correr las pruebas, compilar el APK, instalarlo en el celular y anotar la fase en `BITACORA.md`.

## Comandos útiles

```bash
./gradlew :composeApp:desktopTest            # pruebas
./gradlew :androidApp:assembleRelease        # APK optimizado → androidApp/build/outputs/apk/release/androidApp-release.apk
cp androidApp/build/outputs/apk/release/androidApp-release.apk AURORA-APP/Aurora-0.3.0-android.apk
~/Android/Sdk/platform-tools/adb install -r AURORA-APP/Aurora-0.3.0-android.apk
~/Android/Sdk/platform-tools/adb exec-out screencap -p > captura.png
```

- **Celular de pruebas:** Samsung Galaxy A35 con Android 16 y One UI, navegación de 3 botones, id adb `RFCX90J4NQA`, pantalla de 1080×2340.
  - Si sale "unauthorized", hay que aceptar la depuración USB en el celular.
  - El usuario usa el celular mientras se prueba. Mirar la pantalla antes de tocar, no cambiar sus ajustes sin devolverlos y avisar de cualquier toque equivocado.
- **Capturas sin el celular:** pruebas temporales en `composeApp/src/desktopTest`, con `ImageComposeScene` de 360–420 dp. La variable `AURORA_SHOTS=/ruta` guarda las imágenes. Ejemplos: `ScrollMemoryTest.kt` y `ContextMenuTest.kt`.

## Estado actual (2026-10-07)

- **App ligera en Linux y Android (HECHA, sin commit):** cola con `LazyColumn` (abría lento y retenía 727 MB de portadas), tempo en paralelo y en segundo plano, APK de release con R8, sin respaldo de Android, `postrm` del .deb y `--desinstalar` del AppImage. Detalle en la bitácora. Falta que el usuario lo pruebe.

## Estado anterior (2026-10-06)

- **Configuración inicial nueva (HECHA, fases 1 a 6):** maqueta en `files/Aurora · Configuración inicial.html`; detalle de cada fase en la bitácora. Falta que el usuario la pruebe en el celular y en el PC (ventana 1000×700 y selector de FileKit). Siguiente pedido del usuario: app más ligera (medir primero CPU, memoria y arranque; el tempo de la primera vez en escritorio tarda ~6 s por canción) y después Windows.
- **Barra de progreso:** arreglado el bloqueo en Windows (un solo salto al soltar); falta que el usuario lo pruebe en Windows.
- **Móvil:** terminados los 6 arreglos de los errores del móvil:
  1. botón Atrás y recordar la posición;
  2. video a pantalla completa;
  3. el final de las listas ya no queda tapado;
  4. tarjetas de tema iguales;
  5. ecualizador que suena de verdad en Android;
  6. chips en una línea y sugerencias de carpetas.
- **Notificación y bloqueo:** terminadas las **fases 0 y 1**:
  - el estado vive con el proceso (`AuroraRuntime`);
  - `AuroraSessionPlayer` (un `ForwardingSimpleBasePlayer`) le muestra la cola de Aurora a la sesión;
  - la notificación tiene Me gusta y Aleatorio, portada de 512 px y modo privado en el bloqueo.
- **Widgets:** **fases 2 a 5 hechas** (en `androidMain/widget/`); el usuario confirmó la 2, 3 y 4. Falta que haga las pruebas finales de la fase 5 (lista abajo).
- **Arreglado y confirmado:** la app se congelaba al usar el widget con la app cerrada (bucle entre la sesión y la app; ver la bitácora).
- **Barra del widget:** ahora el tiempo es un Chronometer (avanza solo) y la barra se mueve cada 5 s; los widgets se dibujan en segundo plano con caché de imágenes. Confirmado por el usuario.
- **Onda en la barra del reproductor de la app:** hecha y confirmada por el usuario (no en Póster ni con "Reducir movimiento").
- **Pendiente del usuario:** el celular se desconectó a mitad de una prueba.
  - Hay que volver a encender Ajustes › Reproducción › "Mostrar controles en la pantalla de bloqueo".
  - Hay que probar la tarjeta con el teléfono bloqueado.

## Lo que falta: widgets (Jetpack Glance), fases 2 a 5

- **Pedido original:** el último mensaje largo del usuario sobre widgets, notificación y bloqueo.
- **Maqueta:** `files/Aurora · Widgets, notificación y bloqueo.html` (y su `.md`). Los colores, fondos y radios de cada tema están en las clases `.w.aurora`, `.w.poster`, etc. del CSS.

### Fase 2. Widget "Aurora · Reproductor" 4×2, tema Aurora (HECHA y probada)

- **Dependencias:** agregar `androidx.glance:glance-appwidget` (y `glance-material3` para Material You) a `androidMain`.
- **Receptor:** un `GlanceAppWidgetReceiver` declarado en `androidApp/src/main/AndroidManifest.xml` y su `appwidget-provider` en XML.
- **Contenido 4×2:**
  - portada grande a la izquierda;
  - título, artista y barra de progreso con tiempos;
  - Me gusta, anterior, reproducir/pausa y siguiente;
  - logo pequeño de Aurora arriba a la derecha.
- **Botones:** usar `ActionCallback` que llama a `AuroraRuntime.get(context).player` (`togglePlay`, `next`, `previous`) y a `toggleLike`. Así actúan sin abrir la app. Si el servicio no está activo, arrancarlo.
- **Abrir la app:** tocar la portada o el título abre el reproductor.
- **Estado del widget:** guardar canción, posición, Me gusta, aleatorio y las 2 siguientes con el estado de Glance o DataStore, para verse bien con la app cerrada. `AppState.lastTrack()` ya devuelve la última canción.
- **Cuándo actualizar:** solo al cambiar de canción, al reproducir o pausar y al cambiar Me gusta, Aleatorio o el tema. El progreso, como máximo cada 30 s. Escuchar `app.player.state`, `snapshotFlow { app.liked }` y `settings.theme` desde `AuroraRuntime` o desde `PlaybackService`.
- **Portadas:** como mucho 512 px y comprimidas. `AuroraRuntime.square()` y `AuroraRuntime.jpeg()` ya existen.

### Fase 3. Los demás tamaños (HECHA y probada)

- **"Aurora · Reproductor" en 4×3** (redimensionable con `SizeMode.Responsive`): agrega Aleatorio y "A continuación" con las 2 siguientes, que se pueden tocar para reproducirlas.
- **"Aurora · Barra" 4×1:** portada, título, artista, anterior, reproducir/pausa y siguiente.
- **"Aurora · Portada" 2×2:** la portada ocupa todo, con degradado abajo, título, artista y botón de reproducir/pausa abajo a la derecha.

### Fase 4. Temas, formas y Material You (HECHA y probada)

- **Temas:** el widget sigue el tema elegido en la app. En Aurora, el color de énfasis sale de la portada (`accentOf`).
- **"Estilo de los widgets":** opción nueva en Ajustes › Apariencia, con "Igual que la app" o "Colores del fondo de pantalla". La segunda usa `GlanceTheme` con colores dinámicos en Android 12+, y Aurora en versiones anteriores.
- **Forma de la portada:** se recorta el bitmap antes de enviarlo, porque Glance no recorta. Pétalo arco, Seda círculo, Carbono esquinas cortadas, Póster recta y los demás redondeada.
- **Botón principal:** círculo por defecto, cuadrado en Póster, esquinas cortadas en Carbono y paralelogramo en Estadio. Usar drawables o imágenes.
- **Fuentes del sistema:**
  - Aurora y Bruma: sans-serif negrita.
  - Póster: sans-serif-black en minúsculas.
  - Pétalo y Seda: serif cursiva.
  - Carbono y Estadio: sans-serif-condensed en mayúsculas.
- Al cambiar de tema en la app, todos los widgets se actualizan al instante.

### Fase 5. Pulido (HECHA, faltan las pruebas finales del usuario)

- **Estado vacío:**
  - Sin nada sonando, la última canción con el botón "Continuar".
  - Si nunca sonó nada, "Elige música en Aurora", y al tocar se abre la app.
- **Accesibilidad:** `contentDescription` en todo: "Reproducir", "Pausar", "Siguiente canción", "Me gusta", "Portada de <título>".
- **Selector de widgets:** nombre, descripción corta, `previewImage` y `previewLayout`, con tamaños mínimos y de redimensionado correctos.
- **Pruebas finales:**
  - los 3 widgets en los 7 temas y en Material You, en modo claro y oscuro;
  - redimensionar de 4×2 a 4×3 y ver aparecer la cola;
  - reiniciar el teléfono y que los widgets muestren la última canción;
  - Me gusta y Aleatorio iguales en la notificación, los widgets y la app.
- **Al terminar:** dar la lista de archivos creados y cambiados y los pasos para probar cada parte.

## Otros pendientes conocidos

- **F5, corrección inteligente de datos (en curso):**
  - Fase 1 HECHA y probada: diálogo "Corregir datos" con MusicBrainz (`data/MetadataRepository.kt`), opción en el menú de cada canción y Ajustes › Biblioteca › "Datos de las canciones".
  - Fase 2 HECHA y probada: portadas de Cover Art Archive (miniaturas en los resultados, "Usar la portada del disco", `BlobStore`, `CoverCache.invalidate`) y la canción que suena se actualiza al corregirla.
  - Fase 3: escribir las etiquetas en el archivo también en Android (jaudiotagger con copia temporal y SAF) y "Corregir todas" en lote.
- **"Vigilar cambios" en Android:** no funciona con las carpetas elegidas con el selector del sistema.
- **Git:** primer commit hecho y subido a https://github.com/lial97/proyecto-aurora-app (público, rama `main`). Revisar antes de subir que no entren datos privados.
- **Descargas:** carpeta `AURORA-APP/` (antes `app/`): ahí dejan los instaladores `build-windows.bat` (.exe) y `scripts/empaquetar-linux.sh` (.deb y AppImage), y ahí va el APK. Git solo guarda el APK y `LEEME.md` (los demás están en `.gitignore`) y Releases de GitHub (`gh release create vX.Y.Z ...`): el .deb y el AppImage pasan los 100 MB que GitHub permite dentro del repositorio, por eso van en la Release. Release v0.3.0 publicada con APK, .deb, AppImage y .exe (el .exe se compila en Windows y se sube con `gh release upload v0.3.0 AURORA-APP/Aurora-0.3.0.exe --clobber`).
- **Instaladores de escritorio con VLC adentro (en curso):**
  - Linux HECHO: `bash scripts/empaquetar-linux.sh` (Docker) → `AURORA-APP/Aurora-<versión>-x86_64.AppImage` (se agrega solo al menú al abrirlo) y `AURORA-APP/aurora_<versión>_amd64.deb`.
  - Windows HECHO y probado por el usuario (Ryzen 5 7535HS, 1920×1080 al 125 %): `build-windows.bat` prepara VLC 3.0.24 (`scripts/windows/preparar-vlc.ps1`), arma el instalador y lo deja en `AURORA-APP\`. El `.exe` solo se puede compilar en Windows. `Aurora-0.3.0.exe` subido a la Release v0.3.0.
  - Arreglos de la prueba en Windows (ver la bitácora): VLC decodifica por CPU (con D3D11 el audio del video se cortaba), animaciones que redibujaban sin parar, escritorio más compacto, VLC arranca en segundo plano y el índice de complementos ya sale válido (antes el primer arranque tardaba más de un minuto).
  - **Pendiente:** volver a armar el `.deb` y el AppImage con `bash scripts/empaquetar-linux.sh` para que incluyan estos arreglos (los de la Release son de antes) y subirlos con `gh release upload v0.3.0 AURORA-APP/... --clobber`. Los que hay en `AURORA-APP/` de este PC son de antes de la configuración inicial nueva. Compilar y probar el APK: `App.kt` cambió (en Android solo cambia la decisión del tipo de interfaz con "Compacta").
  - En Windows, `Invoke-WebRequest` de PowerShell 5.1 bajó VLC dañado (SHA-256 distinto) y `curl.exe` lo bajó bien; si vuelve a pasar, cambiar la descarga de `preparar-vlc.ps1` a `curl.exe`.

## Piezas clave del código (para no buscarlas)

- **Estado de la app:** `commonMain/.../AppState.kt`. En Android lo crea `androidMain/.../AuroraRuntime.kt` y lo comparten la actividad, el servicio y los widgets.
- **Cola y lógica de reproducción:** `commonMain/.../player/DefaultPlayerController.kt`.
- **Reproducción en Android:**
  - `androidMain/.../player/PlaybackService.kt`: sesión, notificación y botones propios.
  - `AuroraSessionPlayer.kt`: lo que ve el sistema.
  - `Media3Engine.kt`: el lado de la app, con `MediaController` y la portada.
  - `EqAudioProcessor.kt`: el ecualizador.
- **Temas:** `commonMain/.../theme/Themes.kt`. La forma de las portadas está en `ThemeShapes`.
- **Me gusta:** clave `me_gusta`. **Tema:** clave `tema`. **Aleatorio:** clave `aleatorio`. **Última canción:** clave `ultima_cancion`. Todo va en el almacén clave-valor, que en Android usa DataStore.
