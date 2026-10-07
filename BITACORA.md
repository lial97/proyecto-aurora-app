# Bitácora de cambios

Registro de todo lo que cambia en el proyecto, **lo más nuevo arriba**.
Formato de cada entrada:

```
## AAAA-MM-DD — Título corto
**Qué:** lo que se hizo.
**Por qué:** la razón o el problema que resuelve.
**Archivos:** los principales afectados.
**Pendiente:** lo que queda abierto (opcional).
```

## 2026-10-06 — Windows: desinstalar borra la configuración
**Qué:** al desinstalar y volver a instalar, la app quedaba con la configuración anterior: el instalador no tocaba `%APPDATA%\Aurora` (ajustes, carpetas, portadas) ni `%LOCALAPPDATA%\Aurora\cache` (letras, miniaturas). Ahora:
- `composeApp/windows/main.wxs` es la plantilla de jpackage (JDK 21) con la acción `AuroraBorrarDatos`. Usa `WixQuietExec64`, así que no abre ninguna ventana. Corre después de `RemoveFiles` solo con `REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE`: borra al desinstalar y no al actualizar.
- Tarea nueva `packageWindowsExe` en `build.gradle.kts`: jpackage arma el .exe desde la versión portable (`--app-image`) con esa plantilla. `packageReleaseExe` de Compose no sirve: vacía su carpeta de recursos y la pasa al final, y jpackage usa la última `--resource-dir`. `build-windows.bat` usa la tarea nueva.
**Archivos:** `composeApp/windows/main.wxs`, `composeApp/build.gradle.kts`, `build-windows.bat`, `docs/COMPILAR.md`.
**Pruebas (instalación real con msiexec y un .msi de la misma plantilla):**
- Desinstalar la versión vieja: la configuración quedaba (el error).
- Instalar la nueva, abrirla y desinstalarla: se borran `%APPDATA%\Aurora` y `%LOCALAPPDATA%\Aurora`. El registro de MSI muestra `AuroraBorrarDatos`.
- Instalar 0.3.0, abrirla y actualizar a 0.3.1: la configuración se conserva y queda una sola entrada en Programas.
- Se respaldó y restauró la configuración del usuario.
**Ojo:** la versión 0.3.0 vieja (la de la Release actual) no tiene esta acción. Quien la tenga instalada debe borrar `%APPDATA%\Aurora` a mano una vez, o instalar la nueva y desinstalarla.

## 2026-10-06 — Windows: el audio se trababa al adelantar o atrasar
**Qué:** al saltar con la barra, la canción se entrecortaba solo en Windows. Con el VLC incluido y el registro detallado se vio que, tras cada salto, la salida WASAPI quedaba ~1,3 s atrasada ("playback way too late: flushing buffers" y decenas de "buffer too late: dropped" cada ~1,3 s). La tarjeta de este equipo trabaja a 192 kHz (búfer de 384 000 cuadros para 2 s) y VLC corrige el desfase remuestreando con speex. Comparación con 5 saltos por prueba:
- speex calidad 10: 351 avisos de "tarde" y 327 descartes.
- speex calidad 3: 552 avisos y 444 descartes.
- DirectSound y waveOut: también fallan.
- samplerate: 0 avisos y 0 descartes, con dos canciones distintas. Además, es el que VLC elige por defecto.
Ahora en Windows se usa `--audio-resampler=samplerate,any`; en Linux sigue speex (ahí no pasaba).
**Archivos:** `desktopMain/player/VlcEngine.kt`.
**Pruebas:** 107 pruebas pasan; instalador `AURORA-APP\Aurora-0.3.0.exe` rearmado.
**Pendiente:** que el usuario lo confirme de oído en Windows.

## 2026-10-06 — Compilación y prueba en Windows (commit 20c2484)
**Qué:**
- Arreglo en `build-windows.bat`: la comprobación de Java (`findstr /c:"version \"21" >nul`) fallaba siempre, incluso con Java 21. cmd no entiende `\"`, así que `>nul` quedaba dentro de las comillas y findstr lo tomaba como archivo. Ahora usa `findstr /r /c:"version .21\." >nul`.
- `build-windows.bat` llama a `"%~dp0gradlew.bat"` con la ruta completa. Así funciona aunque cmd no busque en la carpeta actual (`NoDefaultCurrentDirectoryInExePath`).
**Pruebas (Windows 11, 1920×1080 al 125 %, Temurin 21.0.12):**
- `desktopTest`: 107 pruebas, 0 fallos.
- `build-windows.bat` genera `AURORA-APP\Aurora-0.3.0.exe` (99,4 MB). La versión portable lleva VLC con `libvlc.dll`, `libvlccore.dll`, 241 complementos y `plugins.dat`.
- Primera vez (`APPDATA` vacío): la ventana abre en 1000×700 dp centrada, en el paso "Bienvenida", en 2,4 s.
- Ya configurada: la ventana abre en 1280×734 dp (85 % del alto útil) en 3,2 s y muestra la biblioteca y la letra.
- La app carga el `libvlc.dll` de `app\resources\vlc` aunque el equipo tenga VLC instalado en `Program Files`.
**Pendiente (lo prueba el usuario):** recorrer la configuración con el selector nativo de FileKit y ver "Todo listo" con el cálculo del tempo; arrastrar la barra de progreso sin que la app se trabe; reproducir audio y video.

## 2026-10-06 — Configuración inicial nueva (fase 6: pulido, accesibilidad y pruebas)
**Qué:**
- Foco al título de cada paso al llegar (`FocusRequester` + `focusable` + `heading`): los lectores de pantalla lo anuncian y en escritorio el teclado funciona sin hacer clic antes.
- Teclado: Esc y Alt+← vuelven antes que el elemento enfocado; Enter avanza solo si el elemento enfocado no lo usó (`onKeyEvent`), así Enter sobre una tarjeta de tema la elige y en el campo del nombre avanza un solo paso.
- Tarjetas de tema como grupo de opciones navegable: Tab entra, las flechas mueven el foco entre tarjetas y Enter elige.
- Ya estaban de fases anteriores: animación de entrada de 300 ms (sin animación con "Reducir movimiento"), "Paso 2 de 4", `liveRegion` en conteos y etapas, `progressBarRangeInfo`.
**Archivos:** `commonMain/screens/Onboarding.kt`; prueba nueva `desktopTest/OnboardingFlowTest.kt` (recorrido completo con el teclado, Continuar desactivado sin carpetas, tarjetas de tema con flechas, "Todo listo" sin volver atrás, retomar con lo elegido).
**Pruebas:** 107 pruebas de `desktopTest` pasan. Capturas temporales con texto al 130 % en 360 dp (Estadio, Aurora, Póster, Pétalo, Seda) y en escritorio 1000×700: nada se corta ni se superpone y el botón queda visible. APK instalado en el A35.

## 2026-10-06 — Configuración inicial nueva (fase 5: escritorio)
**Qué:**
- Ventana (`desktopMain/Main.kt`): 1000×700 durante la configuración (mínimo 900×620, centrada); al terminar vuelve al tamaño normal (1280×800 o el 85 % de la pantalla, mínimo 800×540) y se centra. La primera vez la ventana ya abre en 1000×700 (`Main.kt` crea los servicios y mira `onboardingDone` antes de abrir). "Repetir configuración inicial" la vuelve a achicar.
- Dos columnas (`DesktopOnboarding`): a la izquierda 330 dp con el logo "Aurora", los 4 pasos (Tu nombre / Cómo te llamamos, Tu música / Carpetas de canciones, Tus videos / Videos musicales, Tu estilo / Elige un tema) con número, el actual resaltado y los hechos con ✓ del color de énfasis, y las portadas en abanico abajo. A la derecha el paso con 56 dp de margen y abajo "Atrás" a la izquierda y el botón principal con "→" a la derecha ("Lo haré después" junto a él). En escritorio el texto va alineado a la izquierda y el resumen final es una fila de 4 tarjetas.
- Móvil y escritorio comparten el contenido de cada paso (`StepBody`); el placeholder del nombre ahora es "Escribe tu nombre", como en la maqueta.
- Selector de carpetas nativo con FileKit 0.16 (`filekit-dialogs`, solo escritorio, ~2 MB): xdg-desktop-portal en Linux (respeta GNOME/KDE) y el diálogo de Windows. Si falla, zenity y por último JFileChooser; si el usuario cancela, no se abre otro.
- Arreglo: en escritorio, elegir una carpeta y cerrar la app a mitad de la configuración hacía que se saltara al volver (la regla de "usuario de versión anterior" veía `carpetas` guardadas). Ahora al abrirse la configuración se guarda `bienvenida_lista=0` (`markOnboardingStarted`).
**Archivos:** `desktopMain/Main.kt`, `desktopMain/platform/DesktopMediaSource.kt`, `commonMain/screens/Onboarding.kt`, `data/SettingsRepository.kt`, `AppState.kt`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `commonTest/data/OnboardingTest.kt`.
**Pruebas:** `desktopTest` pasa; capturas temporales a 1000×700 de los pasos 0–5 en Aurora, Póster, Carbono y Pétalo. `ScrollMemoryTest` falló una vez con un error interno de Compose ("LayoutNode not found in RectList") al correr junto a la prueba de capturas; sola pasa. APK instalado en el A35.
**Pendiente:** probar en el PC real el cambio de tamaño de la ventana y el selector de FileKit (portal) en Linux y en Windows.

## 2026-10-06 — Configuración inicial nueva (fase 4: progreso real del escaneo y tempo)
**Qué:**
- `LibraryState.stage` (`ScanStage`: READING, SORTING, TEMPO; `null` = listo) con `stageDone`/`stageTotal`. La lectura suma el avance de todas las carpetas que se están escaneando (también `scanFolder`, que antes no avisaba); al terminar todas se ordena y empieza el tempo.
- "Todo listo": barra real (lectura 5–50 %, orden 55 %, tempo 60–100 %) con "Leyendo portadas y letras…", "Ordenando por artista y álbum…", "Calculando el tempo de cada canción…" y "Listo.", más "12 de 146" debajo. El mensaje se anuncia con `liveRegion` y la barra tiene `progressBarRangeInfo`.
- Tempo (`domain/Tempo.kt`, `estimateBpm`): flujo espectral cada 10 ms (FFT propia de 512), sin tendencia, autocorrelación 50–220 BPM con preferencia suave por 120 y resultado en 70–180. Solo para canciones sin BPM en la etiqueta, de a una (`limitedParallelism(1)`), y guardado en un solo archivo `tempos` del `BlobStore` (0 = no se pudo, no se reintenta). Se aplica a las pistas al publicar cada escaneo.
- Fragmento para el tempo (`MediaSource.decodeForTempo`, ~20 s del medio, mono):
  - Android: `MediaExtractor` + `MediaCodec`, reducido a ~11 kHz, tope de 6 s por canción.
  - Escritorio (`TempoDecoder`): VLC con el audio a memoria (`amem`, no suena) a ×3 sin conservar el tono; ~6,3 s por canción. Usa el mismo VLC empaquetado (el complemento amem viene con audio_output).
- Primera versión (energía) descartada: con música real daba "sin pulso" o valores lejanos. Comparada en Python contra el BPM de las etiquetas de 8 canciones del PC: el flujo espectral acierta 136, 173 y 146 y da el mismo resultado a ×1 y a ×3; a veces sale la mitad (72 en vez de 144).
**Archivos:** `commonMain/domain/Tempo.kt`, `data/LibraryRepository.kt`, `platform/Platform.kt`, `screens/Onboarding.kt`, `AppState.kt`, `androidMain/platform/AndroidPlatform.kt`, `desktopMain/platform/TempoDecoder.kt`, `desktopMain/platform/DesktopMediaSource.kt`; pruebas `commonTest/domain/TempoTest.kt`, `desktopTest/data/TempoStageTest.kt`.
**Pruebas:** `desktopTest` pasa (pulsos sintéticos a 90/100/120/128 BPM, silencio, etapas y caché). APK instalado en el A35.
**Pendiente:** la primera vez, el tempo de una biblioteca grande tarda (escritorio ~6 s por canción en segundo plano); revisarlo en la fase de app ligera.

## 2026-10-06 — Configuración inicial nueva (fase 3: carpetas, conteo y sugerencias)
**Qué:**
- Pasos 2 y 3: filas con ícono, nombre, ruta monoespaciada con "…", conteo en vivo o "Buscando…" (anunciado con `liveRegion`) y ×; botón grande con borde punteado "Elegir carpeta"/"Añadir otra carpeta" con "Se abre el selector de carpetas del teléfono/del sistema"; nota con el ícono nuevo `AuroraIcon.Shield`.
- Sugerencias "Encontramos música aquí:" / "Encontramos videos aquí:" (`MediaSource.suggestFolders`, `FolderSuggestion`):
  - Escritorio: ~/Música, ~/Music, ~/Descargas, ~/Downloads, ~/Vídeos, ~/Videos (y las de `xdg-user-dir`) y discos en /run/media/<usuario>, /media/<usuario> y /media. Se cuentan todas a la vez con un tope de 2 s en total y solo se muestran las que tienen archivos, con su número. Los conteos completos quedan en memoria. Tocar una la añade directamente.
  - Android: Music y Podcasts (Download solo antes de Android 11, porque desde el 11 el sistema no deja elegir Download entera) para música; Movies para videos. Sin número; al tocarla se abre el selector del sistema ya ubicado ahí (`EXTRA_INITIAL_URI` vía `pickFolder(initial)`).
  - Nunca: `isSuggestibleFolder` ahora también bloquea Pictures/Imágenes y Documents/Documentos (ya bloqueaba DCIM, cámara, capturas, WhatsApp y Telegram).
- Usuarios nuevos de escritorio ya no empiezan con ~/Música y ~/Vídeos elegidas por defecto: aparecen como sugerencias.
- `pickFolder(initial)`: zenity con `--filename` y `JFileChooser` en esa carpeta en escritorio.
**Archivos:** `commonMain/screens/Onboarding.kt`, `platform/Platform.kt`, `components/Icons.kt`, `domain/FileNames.kt`, `AppState.kt`, `desktopMain/platform/DesktopMediaSource.kt`, `androidMain/platform/AndroidPlatform.kt`, `androidApp/.../MainActivity.kt`.
**Pruebas:** `desktopTest` pasa. Sugerencias reales en el PC de desarrollo: Music (157) y Downloads (1) en 0,16 s. Capturas temporales del paso 2 en Aurora, Póster y Carbono. APK instalado en el A35.

## 2026-10-06 — Configuración inicial nueva (fase 2: nombre y estilo con el tema en vivo)
**Qué:**
- Cambio de tema en vivo: se quitó el `Crossfade` de `App.kt` que dibujaba la pantalla dos veces durante 600 ms. Ahora hay una sola copia: los colores pasan en 600 ms (`AppThemeProvider(fadeMs = 600)` mientras dura la configuración, también en Aurora) y las fuentes y formas cambian al instante. `AppThemeProvider` respeta "Reducir movimiento" (0 ms) en toda la app.
- Fondo propio de la configuración: color del tema con dos manchas difuminadas (énfasis y segunda luz), estático. Las decoraciones de Póster y Estadio tapaban el texto.
- Tarjetas de tema (`ThemePreview`, la misma de Ajustes › Apariencia): descripción en 1 línea con "…", alto fijo 120 → 104 dp, grupo de opciones (`selectableGroup`, rol de radio y "elegido").
- Detalles por tema de la maqueta: botón principal con la forma de los chips del tema (Póster recto, Estadio paralelogramo) y esquinas cortadas en Carbono; cuadrito de énfasis antes de la etiqueta en Carbono; portadas del abanico con marco blanco en Pétalo y anillo fino en Seda. El campo del nombre usa la forma de tarjeta.
**Archivos:** `commonMain/App.kt`, `theme/Theme.kt`, `screens/Onboarding.kt`, `screens/SettingsScreen.kt`.
**Pruebas:** `desktopTest` pasa; capturas temporales de los pasos 0, 1 y 4 en Aurora, Póster, Carbono, Estadio, Pétalo y Seda a 360 dp. APK instalado en el A35.

## 2026-10-06 — Configuración inicial nueva (fase 1: estructura, navegación y retomar)
**Qué:**
- `screens/Onboarding.kt` reescrito con 6 pasos (0 Bienvenida, 1 Nombre, 2 Música, 3 Videos, 4 Estilo, 5 Todo listo) y los textos de la maqueta (`files/Aurora · Configuración inicial.html`).
- Móvil: arriba Atrás, barra de 4 tramos y "2/4" (pasos 1–4, anunciado como "Paso 2 de 4"); contenido desplazable; botón principal fijo de 52 dp y secundario debajo ("Lo haré después" en música sin carpetas). Portadas en abanico en bienvenida y final. El contenido aparece subiendo 8 dp con fundido (sin animación con "Reducir movimiento").
- Botones según el paso: "Continuar sin nombre", "Continuar" desactivado hasta tener carpeta y terminar de contar, "Saltar este paso", "Empezar, Lila", "Vamos, Lila"/"Ir a Aurora".
- Retomar: el paso se guarda (`bienvenida_paso`, ahora 0–5). "Empezar" del paso 4 marca la configuración como completada; "Todo listo" no permite volver atrás. Atrás del sistema, Esc y Alt+← vuelven; en la bienvenida Android sale de la app. Enter avanza si el botón está activo.
- Sin carpetas ("Lo haré después"): Inicio y Biblioteca ya no muestran canciones de ejemplo sino "Aún no hay música" con el botón "Elegir carpetas de música" (`LibraryState.noFolders`, `LocalPickFolders`).
- "Repetir configuración inicial" en Ajustes › Acerca de, solo en depuración (`PlatformServices.isDebug`: APK debug en Android; `./gradlew :composeApp:run` en escritorio).
**Archivos:** `commonMain/screens/Onboarding.kt`, `AppState.kt`, `App.kt`, `data/SettingsRepository.kt`, `data/LibraryRepository.kt`, `components/Common.kt`, `screens/SettingsScreen.kt`, `platform/Platform.kt`, `androidMain/platform/AndroidPlatform.kt`, `desktopMain/platform/DesktopPlatform.kt`, `composeApp/build.gradle.kts`, `commonTest/data/OnboardingTest.kt`, `desktopTest/ContextMenuTest.kt` (ahora con una carpeta, porque sin carpetas ya no hay canciones de ejemplo).
**Pruebas:** `desktopTest` pasa; capturas temporales a 360 dp de los 6 pasos y con texto al 130 %. APK compilado; falta probar en el celular (no estaba conectado).
**Pendiente:** fase 2 (vista previa del nombre, cambio de tema en vivo, tarjetas con descripción en 1 línea), fase 3 (sugerencias, escudo, borde punteado), fase 4 (progreso real por etapas), fase 5 (escritorio de dos columnas).

## 2026-10-06 — Barra de progreso: un solo salto al soltar (bug de Windows)
**Qué:**
- `ProgressBar` (`components/Controls.kt`) y `VideoProgress` (`components/Video.kt`): mientras se arrastra, la barra sigue al cursor/dedo con una posición local (sin la animación de 900 ms) y `onSeek` se llama una sola vez al soltar (o al tocar). Mismo patrón que `FullscreenProgress`.
- `VlcEngine.seekTo`: `setTime` va al hilo de VLC (`submit`) y, si llegan varios saltos seguidos, solo se ejecuta el último.
- `DefaultPlayerController`: después de un salto ignora durante 2 s como máximo las posiciones que el motor aún avisa lejos del destino (la barra ya no vuelve atrás un instante).
**Por qué:** en Windows la app se trababa al usar la barra: cada movimiento del arrastre (60–120 por segundo) hacía un `setTime` nativo de VLC en el hilo de la ventana, y con decodificación por CPU cada salto vacía los búferes.
**Archivos:** `commonMain/components/Controls.kt`, `commonMain/components/Video.kt`, `commonMain/player/DefaultPlayerController.kt`, `desktopMain/player/VlcEngine.kt`.
**Pruebas:** `desktopTest` pasa y el APK compila. Falta que el usuario lo pruebe en Windows y en el celular (no estaba conectado).

## 2026-10-06 — Arranque rápido en Windows (VLC en segundo plano e índice de complementos)
**Qué:**
- `VlcEngine` arranca VLC en su propio hilo (`aurora-vlc-arranque`): la ventana ya no espera a VLC. Las órdenes que llegan antes (cargar, reproducir, volumen, ecualizador…) quedan en fila y se ejecutan en orden al terminar. `VlcVideoOutput` existe desde el principio y se conecta con `bind`.
- Índice de complementos de VLC (`plugins.dat`): `preparar-vlc.ps1` lo generaba vacío (24 bytes; `vlc-cache-gen` con ruta relativa no encuentra nada). Ahora usa la ruta completa, pone a los complementos una fecha fija (2020-01-01 UTC) antes de generarlo y falla si sale vacío.
- Gradle (y el instalador) cambian las fechas al copiar: `BundledVlc` restaura la fecha fija al arrancar (solo metadatos, instantáneo). Si no puede, VLC rehace el índice una vez (`--reset-plugins-cache`) con una marca por ubicación en la carpeta de ajustes.
- Las miniaturas de video esperan a que el reproductor deje el índice listo (`BundledVlc.awaitCache`).
**Por qué:** el primer arranque tardaba más de un minuto: `libvlc_new`, en el hilo de la interfaz, abría los 241 complementos (sin índice válido) y Windows Defender revisaba cada uno.
**Archivos:** `desktopMain/player/VlcEngine.kt`, `desktopMain/player/BundledVlc.kt`, `desktopMain/platform/VideoThumbnailer.kt`, `scripts/windows/preparar-vlc.ps1`.
**Pruebas (PC del usuario, copia nueva de la app):** antes, ventana a los 63 s; ahora la ventana sale a los 4–9 s aunque VLC tarde. Con el índice válido `libvlc_new` tarda 33 ms (antes ~57 s en frío) y el arranque en frío con índice válido fue de 4,7 s (ventana) y 5,9 s (VLC). `desktopTest` pasa.

## 2026-10-06 — Escritorio más compacto (fase 2)
**Qué:**
- Escritorio (Windows y Linux) un 10 % más chico de base; "Compacta" quita otro 10 % encima (antes solo existía esta). El tipo de interfaz (escritorio o teléfono) se decide con el ancho que queda después de escalar.
- Ventana inicial: 1280×800 o, si no cabe, el 85 % del área útil de la pantalla. Tamaño mínimo 800×540 (antes 960×600).
- Ajustes: el contenido no pasaba de 760 dp porque `widthIn` iba después de `weight` (que fija el ancho) y se estiraba a toda la ventana. Ahora el desplazamiento ocupa todo el ancho y el contenido como mucho 760 dp. Menú de secciones 220 → 200 dp, separación 28 → 24 dp.
**Por qué:** en la pantalla del usuario (1920×1080 al 125 % = 1536×864 dp) la ventana de 1280×800 la llenaba casi entera y Ajustes tenía mucho espacio vacío.
**Archivos:** `commonMain/App.kt`, `commonMain/screens/SettingsScreen.kt`, `desktopMain/Main.kt`, `ARQUITECTURA.md`.
**Pruebas:** capturas temporales con `ImageComposeScene` a 1920×1032 y 1600×866 px con densidad 1,25 (Inicio, Ajustes › Biblioteca y Apariencia): tres columnas completas y Ajustes en 760 dp. `desktopTest` pasa. Falta compilar y probar en Android (aquí no hay SDK): allí la escala no cambia, solo la decisión del tipo de interfaz con "Compacta".

## 2026-10-06 — Escritorio en Windows: audio del video y CPU en reposo (fase 1)
**Qué:**
- VLC decodifica el video por CPU (`--avcodec-hw=none`, también en las miniaturas). Con la decodificación de la tarjeta gráfica (D3D11 en Windows) VLC no podía pasar los fotogramas a memoria ("Failed to create video converter"), reintentaba, se atrasaba y el audio se entrecortaba ("buffer too late: dropped", "playback too late: upsampling", "flushing buffers").
- Animaciones infinitas que redibujaban la ventana sin parar aunque no sonara nada: ahora solo existen mientras se mueven. Barras de `Equalizer` (en pausa), "respiro" de `heroScale` (en pausa o en temas sin él) y fase de la onda de la barra de progreso (con amplitud 0).
**Por qué:** primera prueba del instalador en Windows (Ryzen 5 7535HS, pantalla 1920×1080 al 125 %): el audio del video se trababa y la app se sentía pesada.
**Archivos:** `desktopMain/player/VlcEngine.kt`, `desktopMain/platform/VideoThumbnailer.kt`, `commonMain/components/Controls.kt`, `commonMain/screens/PlayerScreen.kt`.
**Pruebas:** video 720p30 de 25 s con el VLC empaquetado: antes 754 bloques descartados y 15 "upsampling"; ahora ninguno, 740 fotogramas. CPU en reposo: 1,73 s → 0,14 s cada 10 s; memoria 909 MB → 212 MB. `desktopTest` pasa.
**Pendiente:** que el usuario pruebe el video en Windows. Fase 2: escritorio más compacto.

## 2026-10-06 — Instaladores de escritorio con VLC adentro (fase 2: Windows)
**Qué:**
- `scripts/windows/preparar-vlc.ps1`: descarga VLC 3.0.24 portable de 64 bits (ZIP oficial, comprobado con la huella SHA-256 de VideoLAN y guardado en `%LOCALAPPDATA%\Aurora-compilar` para la próxima vez), copia libvlc.dll, libvlccore.dll y solo los complementos que usa Aurora (sin interfaz, Lua, red, Blu-ray ni grabación) a `composeApp/resources/windows/vlc`, y genera `plugins.dat` con vlc-cache-gen.exe. 140 MB → 63 MB.
- `build-windows.bat`: [1/3] prepara VLC, [2/3] versión portable (`createReleaseDistributable`), [3/3] instalador (`packageReleaseExe`) y lo copia a `dist\`. Ya no pide instalar VLC.
- `.gitattributes`: `.bat` y `.ps1` con fin de línea de Windows (cmd falla en los `goto` con LF), `.sh` y `gradlew` con LF. El `.ps1` lleva marca UTF-8 (PowerShell 5.1).
**Archivos:** `scripts/windows/preparar-vlc.ps1` (nuevo), `build-windows.bat`, `.gitattributes`.
**Pruebas:** el ZIP coincide con la SHA-256 publicada; el script probado con PowerShell en Docker (63 MB, 241 complementos). Falta: compilar y probar el instalador en Windows (jpackage solo arma el .exe en Windows).

## 2026-10-06 — Instaladores de escritorio con VLC adentro (fase 1: Linux)
**Qué:**
- La app busca primero el VLC que viene dentro del instalador (`BundledVlc`: carpeta `vlc` de los recursos de la app, `compose.application.resources.dir`) y, si no está, el del sistema. Fija `VLC_PLUGIN_PATH` en el propio proceso (setenv/_putenv con JNA). Lo usan `VlcEngine` y `VideoThumbnailer`.
- `appResourcesRootDir = composeApp/resources` (`linux/vlc` y `windows/vlc`, fuera de git).
- `scripts/empaquetar-linux.sh` (Docker, Ubuntu 22.04 / glibc 2.35): copia libvlc, los complementos necesarios (sin interfaz Qt, Lua, codificadores, hardware ni red) y todas sus dependencias, menos glibc, gráficos, X11, sonido del sistema y D-Bus; ajusta RUNPATH (`$ORIGIN`) con patchelf, genera `plugins.dat` y arma `dist/aurora_<versión>_amd64.deb` y `dist/Aurora-<versión>-x86_64.AppImage`.
**Archivos:** `desktopMain/player/BundledVlc.kt` (nuevo), `desktopMain/player/VlcEngine.kt`, `desktopMain/platform/VideoThumbnailer.kt`, `composeApp/build.gradle.kts`, `.gitignore`, `scripts/empaquetar-linux.sh`, `scripts/linux/Dockerfile`, `scripts/linux/copiar-vlc.sh`, `scripts/linux/dentro.sh` (nuevos).
**Pruebas:** AppImage (126 MB) y .deb (112 MB) armados; en un contenedor Debian 12 sin VLC, Aurora arranca y carga libvlccore, libavcodec, libFLAC… desde la carpeta de la app.
- El AppImage se agrega solo al menú de aplicaciones al abrirlo (AppRun escribe `~/.local/share/applications/aurora.desktop` con la ruta del AppImage y el ícono en `~/.local/share/aurora/`; se actualiza si el archivo cambia de lugar). `StartupWMClass=app-aurora-MainKt` (comprobado con hyprctl).
**Pruebas en el PC del usuario (Arch + Hyprland):** el AppImage usa el VLC de adentro (`/tmp/.mount_Aurora…/vlc/libvlc.so`, `libvlccore.so.9`, `libvlc_pulse.so.0`) y aparece en el lanzador.
**Pendiente:** probar reproducción real en el escritorio del usuario; fase 2 (Windows: VLC portable dentro del .exe); se puede achicar VLC (163 MB) quitando más complementos.

## 2026-10-06 — Corregir datos: portadas de Cover Art Archive (F5, fase 2 de 3)
**Qué:**
- Los resultados de "Corregir datos" muestran la miniatura del disco (Cover Art Archive, 250 px). Si el elegido tiene portada, aparece "Usar la portada del disco" (activado): al aplicar se descarga a 500 px, se guarda para esa canción y se ve en la app, los widgets y la notificación.
- Portadas guardadas en `BlobStore` (Android: `filesDir/portadas`; escritorio: `~/.config/aurora/portadas`), marcadas con `TrackEdit.customCover` y `Track.coverUri = "aurora:portada"`; `CoverCache` las usa antes que la del archivo. `CoverCache.invalidate()` + `version`: las portadas en pantalla se vuelven a cargar.
- `HttpClient.getBytes` (Android sigue a mano las redirecciones a archive.org; escritorio con `java.net.http`).
- La canción que suena se actualiza al corregirla (`PlayerController.updateTrack`, `MediaEngine.updateMeta`): título, artista, álbum y portada cambian en el reproductor, la notificación y el bloqueo sin cortar el sonido. Los widgets se redibujan al cambiar una portada o el título.
- Arreglo: una corrección nueva borraba la anterior al volver a escanear (`edits.set` reemplazaba todo); ahora se suman (`TrackEdit.mergedOnto`).
**Archivos:** `platform/Platform.kt` (`getBytes`, `BlobStore`, `files`), `data/MetadataRepository.kt` (`cover`), `data/EditsRepository.kt`, `domain/Models.kt` (`TrackEdit`), `components/Cover.kt`, `components/Dialogs.kt`, `AppState.kt`, `player/*` (`updateTrack`, `updateMeta`), `androidMain/platform/AndroidPlatform.kt`, `androidMain/player/Media3Engine.kt`, `androidMain/widget/WidgetUpdater.kt`, `desktopMain/platform/DesktopPlatform.kt`, `desktopTest/CustomCoverTest.kt` (nueva).
**Pruebas:** `CustomCoverTest` (las correcciones se suman y recuerdan la portada; aplicar descarga `front-500`, la guarda y la caché la devuelve); descarga real de Cover Art Archive (redirección a archive.org, JPEG 250×250); APK instalado.

## 2026-10-06 — Corregir datos con MusicBrainz (F5, fase 1 de 3)
**Qué:**
- Diálogo "Corregir datos" (`AppDialog.FixMetadata`): título y artista con la sugerencia del nombre del archivo si los datos son dudosos ("Título - Artista (320).mp3"), búsqueda en MusicBrainz al abrir y con "Buscar", hasta 8 resultados (título, artista · álbum · año, duración; la duración se atenúa si no se parece a la del archivo) y "Aplicar" → título, artista, álbum, año y género con `editTrack` (se guardan aunque se vuelva a escanear y se vuelve a buscar la letra). En escritorio, opción de guardarlo también en el archivo. "Editar a mano" abre "Editar datos".
- `MetadataRepository`: búsqueda `recording:"…" AND artist:"…"` (sin "(320)", "(En Vivo)", etc.), User-Agent propio, como mucho 1 petición por segundo y reintentos si el servicio está ocupado. Elige el disco más "original" (oficial, álbum, no recopilatorio ni en vivo, el más antiguo) y ordena por coincidencia y por duración parecida (±5 s).
- Menú de cada canción: "Corregir datos…" (resaltado si los datos son dudosos) y "Editar datos…" (antes solo estaba en el escritorio).
- Ajustes › Biblioteca › "Datos de las canciones": cuántas tienen datos dudosos y "Revisar" (lista para corregirlas una por una).
**Notas:** la búsqueda de MusicBrainz casi nunca trae el género: en ese caso no se cambia.
**Archivos:** `data/MetadataRepository.kt` (nuevo), `AppState.kt` (`metadata`, `applyMetadata`, `tracksNeedingFix`, `FixMetadata`, `TracksToFix`), `components/Dialogs.kt`, `screens/SettingsScreen.kt`, `commonTest/MetadataRepositoryTest.kt` (nueva).
**Pruebas:** `MetadataRepositoryTest` (5: lectura de una respuesta real, disco original, orden por duración, sin conexión ≠ sin resultados, la búsqueda no envía "(320)"); consulta real a MusicBrainz; captura del diálogo a 400 dp.
**Pendiente:** fase 2 (portadas de Cover Art Archive) y fase 3 (escribir en el archivo en Android y corregir en lote).

## 2026-10-06 — Letras: descarga automática al sonar, guardado como .lrc y arreglos en Android
**Qué:**
- La letra se busca al empezar cada canción desde `AppState` (antes desde la pantalla, `App.kt`): también con la app cerrada (widgets, notificación).
- Opción nueva en Ajustes › Letras: "Guardar como archivo .lrc" (activada por defecto, `autoSaveLrc`, clave `pref.guardar_lrc`): la letra sincronizada descargada de LRCLIB se guarda como "canción.lrc" junto a la canción. "Descargar letras" pasa a llamarse "Descargar letras automáticamente".
- Android, error del .lrc: no existía `saveLyricsFile` (el botón "Guardar .lrc" siempre fallaba) y no se leían los .lrc junto a las canciones. Ahora el escaneo lee el .lrc con el mismo nombre (DocumentsContract) y se guarda con el permiso de la carpeta (crea o reemplaza el archivo).
- Las carpetas se guardaban con permiso solo de lectura (`persisted=0x1` en el celular, aunque el sistema ofrecía lectura y escritura). Ahora se pide lectura y escritura; para las carpetas ya elegidas aparece "Permiso para guardar" › "Dar permiso" (volver a elegir la carpeta) y, si falla un guardado, un aviso una sola vez.
- Las letras descargadas se guardan en `filesDir/letras` (antes `cacheDir`, que Android puede borrar cuando falta espacio); las que había se mudan solas.
**Archivos:** `platform/Platform.kt` (`foldersWithoutWrite`), `androidMain/platform/AndroidPlatform.kt`, `AppState.kt` (`autoSaveLrc`, `grantLyricsWrite`), `App.kt`, `data/Prefs.kt`, `screens/SettingsScreen.kt`.
**Pruebas:** `desktopTest` pasa; en el celular, tras "Dar permiso" (Music/Telegram queda con `persisted=0x3`), al sonar "Arbol Sin Hojas" se creó "Arbol Sin Hojas - Dread Mar I (320).lrc" junto al .mp3 (39 líneas sincronizadas).

## 2026-10-06 — Reproductor: barra de progreso con onda (como la de Android 13+)
**Qué:** en el reproductor del móvil, la parte ya escuchada de la barra es una onda (≈28 dp de largo, 3 dp de alto, como en la maqueta) que avanza mientras suena, con una marca vertical en la posición; el resto sigue recto. Al pausar, la onda se aplana en 450 ms y vuelve la barra recta con la bolita. Usa el degradado de relleno de cada tema; en Carbono las puntas son rectas. No se usa en Póster (su barra es un bloque de 12 dp) ni con Accesibilidad › "Reducir movimiento".
**Por qué:** el usuario preguntó por qué la tarjeta de One UI no se ve como la maqueta: esa tarjeta la dibuja Samsung y ninguna app puede cambiarla; la onda se agregó dentro de la app, donde sí se controla el dibujo.
**Archivos:** `components/Controls.kt` (`ProgressBar(wavePlaying)`, `drawWave`), `screens/PlayerScreen.kt`, `desktopTest/WaveProgressTest.kt` (nueva).
**Pruebas:** `WaveProgressTest` (capturas en los 7 temas, sonando y en pausa); el usuario confirmó en el celular que funciona.

## 2026-10-06 — Widgets: barra de progreso más fluida y sin trabar la app
**Qué:**
- El tiempo transcurrido es un `Chronometer` de Android (`AndroidRemoteViews`, `layout/widget_tiempo.xml`): avanza solo cada segundo sin redibujar el widget. Tiempos en formato 01:18.
- La barra de progreso se mueve cada 5 s mientras suena y la pantalla está encendida (antes cada 15 s).
- Los widgets se arman fuera del hilo principal (`Dispatchers.Default`).
- Caché de imágenes (`cached`, LruCache de 12 MB): la portada se lee, se escala al tamaño real en pantalla y se recorta una sola vez por canción, tema y tamaño (antes, en cada actualización y a 512 px).
**Por qué:** medido con logcat: el primer dibujo llegaba a los 0,5 s de empezar la canción, pero después la barra no se movía hasta 15–17 s más tarde. Además cada actualización tardaba 1,5 s en el hilo principal ("Skipped 99 frames"): la app se trababa.
**Archivos:** `androidMain/widget/WidgetUi.kt`, `WidgetUpdater.kt`, `PlayerWidget.kt`, `CoverWidget.kt`, `res/layout/widget_tiempo.xml` (nuevo).
**Pruebas:** `desktopTest` pasa; APK instalado.

## 2026-10-06 — Arreglo: la app se congelaba ("no responde") al usar el widget con la app cerrada
**Qué:** las órdenes del MediaController de la propia app (`Media3Engine`) llevan la señal de conexión `APP_CONTROLLER`; `AuroraSessionPlayer` ya no reenvía esas órdenes (reproducir/pausa, saltos, aleatorio) al controlador de la app, solo las de fuera (notificación, bloqueo, auriculares) (`fromApp`, con `controllerForCurrentRequest`).
**Por qué:** 5 ANR en el Samsung (14:03, 14:04, 14:28, 14:29, 14:32). Las pilas (`adb bugreport`, `/data/anr`) mostraron un bucle sin fin en el hilo principal: `togglePlay` → `Media3Engine.play` → sesión → `AuroraSessionPlayer.handleSetPlayWhenReady` → `togglePlay`… Con la app cerrada, los toques del widget dejaban órdenes en cola mientras el controlador se conectaba; al llegar, el estado de la app y el de ExoPlayer no coincidían y cada orden de la app volvía a la app como si fuera de fuera, invirtiendo reproducir/pausa sin parar.
**Archivos:** `androidMain/player/AuroraSessionPlayer.kt`, `Media3Engine.kt`, `PlaybackService.kt`.
**Pruebas:** `desktopTest` pasa; el usuario confirmó en el celular que ya no se congela.

## 2026-10-06 — Android: pulido de los widgets (widgets, fase 5)
**Qué:**
- Estado vacío: con nada cargado (app cerrada, recién abierta o tras reiniciar) los widgets muestran la última canción con el botón "Continuar" (en 4×2/4×3 y 4×1 en lugar de los controles; en 2×2 el botón principal dice "Continuar" a TalkBack). Continuar retoma la cola guardada (o la última canción) sin abrir la app. Si nunca sonó nada: "Elige música en Aurora", que abre la app.
- Accesibilidad: descripción en todos los botones ("Reproducir", "Pausar", "Continuar", "Canción anterior", "Siguiente canción", "Me gusta"/"Quitar de Me gusta", "Aleatorio", "Portada de <título>") y en las filas de "A continuación" ("Reproducir <título>, de <artista>").
- Selector de widgets: nombre y descripción corta; en Android 15+ vista previa generada con el widget real y una canción de ejemplo (`providePreview` + `setWidgetPreviews`, una vez por versión: `PREVIEWS_VERSION`); en Android 12–14 `previewLayout` (plantillas XML con el estilo Aurora); en Android 11 o anterior `previewImage`. Tamaños: Reproductor 4×2 (mín. 250×110 dp, se agranda a 4×3), Barra 4×1 (mín. 250×40, solo a lo ancho), Portada 2×2 (mín. 110×110).
**Archivos:** `androidMain/widget/WidgetUi.kt` (`ContinueButton`, `restingNow`), `WidgetState.kt` (`resting`, `preview()`), `WidgetUpdater.kt` (`publishPreviews`), `PlayerWidget.kt`, `BarWidget.kt`, `CoverWidget.kt`, `res/layout/widget_preview_*.xml` y `res/drawable/widget_preview_*.xml` (nuevos), `res/xml/widget_*.xml`.
**Pruebas:** `desktopTest` pasa; APK instalado; tras instalar, los 3 widgets se redibujan sin errores en logcat.
**Pendiente:** pruebas finales del usuario (temas claro/oscuro y Material You, 4×2 ↔ 4×3, reinicio, Me gusta y Aleatorio iguales en todos lados); la barra de progreso que tarda en aparecer.

## 2026-10-06 — Android: widgets con los temas, formas y Material You (widgets, fase 4)
**Qué:**
- Los 3 widgets siguen el tema de la app con los colores, radios y bordes de la maqueta (`WidgetStyle` en `WidgetTheme.kt`): Aurora (degradado y énfasis de la portada), Póster (borde de 2,5 dp), Pétalo, Seda (borde fino), Carbono, Estadio y Bruma.
- Forma de la portada recortada en el bitmap (`shapeCover`): Pétalo arco, Seda círculo, Carbono esquinas cortadas, Póster recta y los demás redondeada (Estadio 6 dp, Aurora/Bruma 14 dp). Las de "A continuación" usan la mitad del radio. En 2×2 la portada ocupa todo, sin forma propia.
- Botón principal: círculo por defecto, cuadrado en Póster, esquinas cortadas en Carbono y paralelogramo (más ancho) en Estadio (`widget_play_*`).
- Fuentes del sistema: Aurora y Bruma sans-serif negrita; Póster sans-serif-black en minúsculas; Pétalo y Seda serif cursiva; Carbono y Estadio sans-serif-condensed en mayúsculas (el título).
- Ajustes › Apariencia › Widgets › "Estilo de los widgets": "Igual que la app" o "Colores del fondo de pantalla" (`widgetsWallpaper`, clave `pref.widgets_fondo`). Material You con `GlanceTheme.colors` en Android 12+ (claro y oscuro solos); en versiones anteriores se ve como Aurora.
- Cambiar el tema o el estilo vuelve a dibujar todos los widgets al instante.
**Archivos:** `androidMain/widget/WidgetTheme.kt` (nuevo), `WidgetUi.kt`, `PlayerWidget.kt`, `BarWidget.kt`, `CoverWidget.kt`, `WidgetState.kt`, `WidgetUpdater.kt`, `res/drawable/widget_play_square.xml`, `widget_play_cut.xml`, `widget_play_parallelogram.xml` (nuevos), `data/Prefs.kt`, `screens/SettingsScreen.kt`.
**Pruebas:** `desktopTest` pasa; APK instalado; captura en el celular con el tema Póster (los 3 widgets bien).
**Notas:** One UI redondea las esquinas de los widgets con su propio radio, así que en Póster (4 dp) se ven más redondeadas que en la maqueta.
**Pendiente:** ver los demás temas y Material You en el celular.

## 2026-10-06 — Android: widgets 4×3, Barra 4×1 y Portada 2×2 (widgets, fase 3)
**Qué:**
- "Aurora · Reproductor" al agrandarlo (desde 260 dp de alto; en Samsung 4×2 mide 206 dp y 4×3 unos 310 dp): portada de 84 dp con título, artista y progreso arriba; Aleatorio, anterior, reproducir/pausa, siguiente y Me gusta en medio; abajo "A CONTINUACIÓN" con las 2 siguientes (portada pequeña, título · artista), que al tocarlas se reproducen (`play(cola, índice)` dentro de la misma cola). Sin más canciones: "No hay más canciones en la cola".
- "Aurora · Barra" (4×1): portada, título, artista, anterior, reproducir/pausa y siguiente.
- "Aurora · Portada" (2×2): la portada ocupa todo, degradado oscuro abajo, título, artista y reproducir/pausa abajo a la derecha.
- Piezas comunes en `WidgetUi.kt` (colores, botones, portadas redondeadas, fondos, estado vacío, receptores y acciones). Portadas de las 2 siguientes guardadas a 128 px (`widget_siguiente_*.jpg`; se borran las que ya no hacen falta).
- Se usa `SizeMode.Exact` (no `Responsive`): el widget se arma en el proceso con el tamaño real que da el lanzador. Al cambiar el tamaño se vuelve a dibujar enseguida (`onAppWidgetOptionsChanged`).
**Archivos:** `androidMain/widget/WidgetUi.kt`, `BarWidget.kt`, `CoverWidget.kt` (nuevos), `PlayerWidget.kt`, `WidgetUpdater.kt`, `WidgetState.kt`, `res/xml/widget_barra.xml` y `widget_portada.xml` (nuevos), `res/values/widget_strings.xml`, `androidApp/.../AndroidManifest.xml`, `androidApp/.../MainActivity.kt`.
**Pruebas:** `desktopTest` pasa; APK compilado.
**Pendiente:** la barra de progreso del widget sigue tardando en aparecer (el usuario lo deja para después); probar los 3 widgets en el celular.

## 2026-10-06 — Android: widget "Aurora · Reproductor" 4×2, tema Aurora (widgets, fase 2)
**Qué:**
- Widget con Jetpack Glance 1.2.0: portada grande redondeada a la izquierda (abre el reproductor), título y artista (también abren el reproductor), barra de progreso con tiempos, Me gusta, anterior, reproducir/pausa (círculo claro) y siguiente; logo pequeño de Aurora arriba a la derecha. Fondo en degradado a 140° desde el color oscuro de la portada; la barra y el Me gusta activo usan el énfasis de la portada (`accentOf`).
- Los botones actúan sin abrir la app (`ActionCallback` → `AuroraRuntime.get(context).player`). Si la app estaba cerrada, se crea el estado, se esperan hasta 7 s a que vuelva la cola y, si no vuelve, se usa la última canción.
- Lo que muestra se guarda (`WidgetStore`: SharedPreferences `aurora_widgets` y la portada en `widget_portada.jpg`, 512 px). Con el proceso muerto (p. ej. tras reiniciar) se muestra en pausa. Si nunca sonó nada: "Elige música en Aurora" (abre la app).
- Solo se dibuja al cambiar de canción, reproducir/pausar, Me gusta, Aleatorio, la cola, el tema o cuando se conoce la duración real; mientras suena y la pantalla está encendida, el progreso cada 15 s (con la pantalla apagada, nada) (`WidgetUpdater`). Si no hay widgets puestos no hace nada.
- **Arreglo de lentitud:** la primera actualización tardaba 20 s (en los registros: la canción sonó a las 13:59:02 y el widget cambió a las 13:59:22) porque la sesión de Glance va por WorkManager, que Android retrasa con la app en segundo plano. Ahora el widget se arma en el proceso (`GlanceAppWidget.compose`, vertical y horizontal) y se entrega con `AppWidgetManager.updateAppWidget`; si falla, se usa la sesión normal.
- Abrir el reproductor desde fuera: `AppState.requestPlayer()` (espera hasta 10 s a la cola) → `MobileApp` abre la capa del reproductor (o del video).
**Archivos:** `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `androidMain/widget/PlayerWidget.kt`, `WidgetState.kt`, `WidgetUpdater.kt` (nuevos), `androidMain/res/` (íconos `widget_ic_*`, `widget_circle`, `xml/widget_reproductor.xml`, `values/widget_strings.xml`, nuevos), `androidMain/AuroraRuntime.kt` (`peek`, arranca el actualizador), `AppState.kt`, `MobileApp.kt`, `androidApp/.../AndroidManifest.xml`, `androidApp/.../MainActivity.kt`.
**Pruebas:** `desktopTest` pasa; APK instalado en el Samsung A35 y Android registra el widget.
**Pendiente:** que el usuario lo ponga en la pantalla de inicio y pruebe los botones (también con la app cerrada desde Recientes); fases 3–5.

## 2026-10-06 — Android: notificación y pantalla de bloqueo (fases 0 y 1)
**Qué:**
- Fase 0: el estado de la app (cola, Me gusta, aleatorio, ajustes) vive con el proceso (`AuroraRuntime`), no con la pantalla; la actividad solo lo muestra (`App(external = …)`). Contar reproducciones, guardar el punto para retomar y restaurar la cola pasaron de la pantalla a `AppState`. Se guardan el aleatorio (`aleatorio`) y la última canción (`ultima_cancion`).
- La sesión ve la cola de Aurora: `AuroraSessionPlayer` (`ForwardingSimpleBasePlayer` sobre ExoPlayer) reenvía siguiente, anterior (reinicia si van más de 3 s), aleatorio, reproducir/pausa y mover la barra al controlador de la app. Si Android pausa por su cuenta (llamada, otra app, auriculares), la app se entera (`setOnPlayingChanged`).
- Notificación de Media3 (`DefaultMediaNotificationProvider`) con Me gusta (izquierda) y Aleatorio (derecha): `SessionCommand` propios y `setMediaButtonPreferences`; el sistema solo los toma si admiten `SLOT_OVERFLOW`. Cambian al instante.
- Metadatos: título, artista, álbum, duración y portada JPEG de 512×512 (o la de Aurora), que se agrega sin cortar el sonido (`replaceMediaItem`). Video sin artista: solo el nombre.
- Ajustes › Reproducción › "Mostrar controles en la pantalla de bloqueo" (activado): apagado, la notificación es privada y su versión pública dice "Aurora · Reproduciendo".
- En pausa la notificación se puede descartar enseguida (`setForegroundServiceTimeoutMs(0)`). Ya no se pide POST_NOTIFICATIONS (comprobado: con el permiso negado la tarjeta aparece igual en Android 16).
**Archivos:** `AppState.kt`, `App.kt`, `data/Prefs.kt`, `screens/SettingsScreen.kt`, `components/Icons.kt`, `player/MediaEngine.kt`, `player/DefaultPlayerController.kt`, `androidMain/AuroraRuntime.kt` (nuevo), `androidMain/player/AuroraSessionPlayer.kt` (nuevo), `androidMain/player/PlaybackService.kt`, `androidMain/player/Media3Engine.kt`, `androidApp/.../MainActivity.kt`.
**Pruebas (Samsung A35, Android 16):** teclas multimedia (reproducir, siguiente, anterior con reinicio, pausa); Me gusta y Aleatorio desde la tarjeta; cerrar la app desde Recientes y seguir controlando; descartar en pausa; modo privado; notificación sin POST_NOTIFICATIONS.
**Pendiente:** ver la tarjeta con el teléfono bloqueado (lo prueba el usuario); widgets (fases 2–5).

## 2026-10-06 — Chips y textos en una línea, decoraciones detrás, sugerencias de carpetas (arreglo 6 de 6)
**Qué:**
- Los chips nunca parten su texto (`softWrap = false`); las filas de chips pasan a la línea siguiente (`FlowRow`): formatos, presets del ecualizador, opciones (Densidad, Al terminar la cola), botones de carpetas, Acerca de (chips y etiquetas) y Detalles de una pista.
- En el móvil los controles anchos (deslizadores y opciones) van debajo del nombre del ajuste ("Fundido" ya no se parte).
- Las etiquetas de las pestañas van en una línea y se achican un poco con texto grande (`FitText`).
- Tarjetas y chips con fondo transparente (Póster, Seda) pintan antes el fondo del tema: las decoraciones quedan siempre detrás y no se ven a través del texto (en Aurora no: su vidrio es a propósito).
- Las sugerencias de carpetas nunca incluyen WhatsApp, Telegram, capturas, cámara (DCIM), GIF ni grabaciones (`isSuggestibleFolder`). En Android no hay sugerencias (solo el selector del sistema) y el contador cuenta las carpetas reales (arreglos anteriores).
- Texto de "Evitar saturación": "Baja el volumen general lo justo si una banda sube".
**Archivos:** `components/Controls.kt` (`surface`, `opaqueBase`, `Chip`), `components/Common.kt` (`FitText`), `components/Rows.kt`, `components/Dialogs.kt`, `screens/SettingsScreen.kt`, `domain/FileNames.kt`.
**Pruebas:** `SuggestedFoldersTest`; capturas a 360 dp y 130 % en Póster y Carbono (Biblioteca, Sonido, Reproducción, Acerca de).

## 2026-10-06 — Ecualizador: se ve bien en el móvil y suena de verdad en Android (arreglo 5 de 6)
**Qué:**
- Editor: las 11 columnas (Pre + 10 bandas) se reparten el ancho con `weight`, Pre un poco separado; la curva usa los mismos centros y la misma altura útil que las perillas (con todo en 0 es una línea recta). Valores y frecuencias en una línea (10,5 sp, enteros en el móvil, tamaño de texto limitado a +15 % ahí), perillas de 22 dp y toda la columna responde al dedo. "Activado/Desactivado" en una línea.
- El aviso de saturación dice "el volumen general baja X dB" (no "el preamplificador", cuyo control va de −20 a +8) y solo aparece si alguna banda sube.
- Sin la línea naranja de la decoración de Carbono en el móvil. Textos de Sonido y Reproducción sin "VLC" en Android ("Audio con Media3").
- Elegir un preset en Ajustes › Sonido enciende el ecualizador (como en el reproductor).
- **Sonido real en Android:** `TenBandEq` (código común, 10 filtros peaking RBJ de una octava en las frecuencias de VLC + ganancia con la misma protección) dentro de un `AudioProcessor` de Media3 (`EqAudioProcessor`, PCM 16 bits y float) en el `DefaultAudioSink` del servicio. Nunca deja pasar muestras fuera de −1…1. Apagado no toca el audio.
**Por qué:** cada banda medía 44 dp fijos (484 dp en total, no cabían en 360 dp) y la curva calculaba sus posiciones con otro ancho; en Android el motor de Media3 no tenía ecualizador. El "32 dB" era real (VLC baja hasta 32 dB desde su +12) pero se leía como si el preamplificador saliera de su rango.
**Archivos:** `components/EqualizerEditor.kt`, `components/Backdrop.kt`, `screens/SettingsScreen.kt`, `audio/EqDsp.kt` (nuevo), `androidMain/player/EqAudioProcessor.kt` (nuevo), `androidMain/player/PlaybackService.kt`, `androidMain/player/Media3Engine.kt`.
**Pruebas:** `EqDspTest` (apagado/plano no cambia nada, +6 dB en 1 kHz sube 6 dB y no toca 62 Hz, cortes, sin recorte con todo al máximo, la protección evita el recorte del preset más grave); capturas a 360 dp y 130 %; en el celular: el procesador se configura con el audio real (44,1 kHz, 2 canales), recibe el preset y queda activo al encender el ecualizador.

## 2026-10-06 — Tarjetas de tema iguales (arreglo 4 de 6)
**Qué:** las tarjetas del selector de tema (Ajustes › Apariencia y bienvenida, mismo componente) miden siempre 120 dp de alto, en cualquier pantalla y tamaño de texto: cada tema usa su fuente pero con tamaño y alto de línea fijos (`LineHeightStyle` centrado y recortado), el nombre en 1 línea y la descripción en 2 como mucho, con "…". El tamaño de texto de Accesibilidad no cambia la tarjeta. Todas usan la forma de la app (la de cada tema se ve en la portada de muestra). Descripciones cortas nuevas. El título "TEMA" ya no aparece cortado (cada sección de Ajustes tiene su propio desplazamiento, arreglo 1).
**Por qué:** cada fuente (Fraunces, Cormorant, Bebas Neue…) tiene alturas distintas y la tarjeta no tenía alto fijo; el fondo interior de Póster usaba su forma cuadrada y se salía del borde redondeado.
**Archivos:** `screens/SettingsScreen.kt` (`ThemePreview`), `theme/Themes.kt`.
**Pruebas:** capturas a 360 dp con texto al 100 % y 130 % (Ajustes y bienvenida, temas Aurora, Bruma y Seda).

## 2026-10-06 — Móvil: el final de las listas ya no queda tapado (arreglo 3 de 6)
**Qué:** el relleno inferior de todas las listas se mide en vez de ser fijo: alto real del bloque de abajo (mini reproductor + pestañas + barra de navegación del sistema, de gestos o de 3 botones) + 16 dp. Cambia solo si el mini reproductor aparece o desaparece. Todo el bloque tiene fondo sólido, también el hueco entre el mini reproductor y las pestañas. El mini video flotante usa el mismo valor.
**Por qué:** el relleno era 160/96 dp fijo y no incluía la barra de navegación del sistema (≈48 dp con 3 botones); además el hueco de 10 dp sobre las pestañas era transparente.
**Archivos:** `MobileApp.kt`.
**Pruebas:** capturas a 360 dp y texto al 130 % (Canciones, Álbumes, Inicio, Ajustes › Biblioteca); en el celular (3 botones) Canciones, Álbumes y Artistas llegan al final con el último elemento completo.

## 2026-10-06 — Móvil: video en pantalla completa (arreglo 2 de 6)
**Qué:** pantalla completa nueva en el móvil (`FullscreenVideo`): arriba salir y título; al centro anterior, reproducir/pausa y siguiente; abajo progreso con tiempo actual y total y botón de salir; degradados oscuros; todo dentro de `safeDrawing` (barras del sistema y recorte de la cámara). Los controles se ocultan a los 3 s con fundido de 250 ms, tocar los muestra u oculta, no se ocultan en pausa ni mientras se arrastra la barra, y al pausar (también desde la notificación) reaparecen. Doble toque izquierda/derecha: −10 s / +10 s. Atrás sale de pantalla completa. En Android: modo inmersivo (`WindowInsetsControllerCompat`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) y giro a horizontal si el video es horizontal (tamaño leído de Media3). Si pasa a sonar una canción, sale solo de pantalla completa. Sin artista, el título es solo el nombre del archivo.
**Por qué:** en Android la acción "pantalla completa" no estaba conectada (solo en escritorio), así que las barras del sistema tapaban la barra de progreso y no había forma de salir. El mini video "fantasma" era el SurfaceView de Media3, que no respeta la transparencia ni el recorte de Compose: ahora se usa `ContentFrame` con TextureView (media3-ui-compose).
**Archivos:** `components/FullscreenVideo.kt` (nuevo), `components/Video.kt`, `domain/FileNames.kt` (`videoCaption`), `player/MediaEngine.kt` (`videoSize`), `MobileApp.kt`, `androidMain/player/Media3Engine.kt`, `androidMain/NativeVideo.android.kt`, `androidApp/.../MainActivity.kt`, `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`.
**Pruebas:** `VideoCaptionTest`; capturas en vertical y horizontal (en pausa, sonando y ocultos a los 3 s).

## 2026-10-06 — Móvil: botón Atrás y recordar la posición (arreglo 1 de 6)
**Qué:** Atrás ya no cierra la app: cierra diálogo/hoja → pantalla completa → panel Letra/Cola/Info → subpágina (Ajustes, álbum, artista) → reproductor o video → lista abierta → pestaña anterior → Inicio; en Inicio la app pasa a segundo plano (`moveTaskToBack`, la música sigue). El reproductor y el video son una capa encima: la pantalla de abajo no se destruye. Cada pestaña guarda su estado (`SaveableStateHolder`), cada categoría de la Biblioteca y cada álbum/artista tienen su propia lista y posición, cada sección de Ajustes su desplazamiento; cambiar el orden vuelve arriba. Las categorías quedan fijas arriba al bajar.
**Por qué:** el contenido se recreaba al abrir el reproductor (`None` → `null` en el estado de la animación), las listas guardaban la posición dentro de la pantalla y morían al cambiar de pestaña, y las categorías compartían una sola lista. Además, tocar un chip dentro de un encabezado fijo de la lista hacía que la lista se desplazara para "mostrarlo".
**Archivos:** `MobileApp.kt`, `AppState.kt`, `screens/LibraryScreen.kt`, `screens/SettingsScreen.kt`, `screens/PlayerScreen.kt`, `platform/Platform.kt`, `androidMain/platform/AndroidPlatform.kt`, `androidApp/.../MainActivity.kt`, `composeApp/build.gradle.kts`, `desktopTest/ScrollMemoryTest.kt` (nueva).
**Pruebas:** `ScrollMemoryTest` con 2.000 canciones (reproductor, pestañas, categorías); Atrás probado en el celular con adb.

## 2026-10-05 — Bienvenida y nombre del usuario
**Qué:** bienvenida de 4 pasos (nombre, carpetas de música, carpetas de videos, tema con transición de 600 ms), retoma el paso si se cierra la app y Atrás del sistema vuelve al paso anterior. Carpetas de música y de videos separadas: de las de música solo se toma audio y de las de videos solo video; cada carpeta se busca por separado en segundo plano y muestra cuántos archivos encontró. Saludo con nombre ("Buenas noches, Lila") en una línea que se achica o corta con "…"; tocarlo cambia el nombre. Fila "Tu nombre" en Ajustes. Se quitó el círculo "AL" en móvil y escritorio.
**Archivos:** `screens/Onboarding.kt`, `App.kt`, `data/SettingsRepository.kt`, `data/LibraryRepository.kt`, `AppState.kt`, `screens/SettingsScreen.kt`, `screens/HomeScreen.kt`, `screens/Greeting.kt`, `components/Common.kt`, `components/Dialogs.kt`, `desktop/Chrome.kt`, `desktop/DesktopViews.kt`.
**Android:** selector de carpetas del sistema (ACTION_OPEN_DOCUMENT_TREE) con el permiso guardado (takePersistableUriPermission); se recorren solo esas carpetas y sus subcarpetas con DocumentsContract, ya no todo el almacenamiento. Los datos se toman del índice de Android si el archivo está en él; si no, del propio archivo (MediaMetadataRetriever, con portada incrustada). Ajustes guardados con DataStore (migra lo que había en SharedPreferences). Las carpetas se muestran como "Almacenamiento interno/Music/Rock".
**Pruebas:** `OnboardingTest` (nombre de 20 caracteres, retomar paso, usuarios anteriores, carpetas separadas, saludo por hora).
**Pendiente:** probar en el celular; "Vigilar cambios" no funciona aún con carpetas del sistema en Android.

## 2026-10-05 — Móvil más ordenado (diseño "Aurora móvil")
**Qué:**
- Biblioteca: chips Canciones/Álbumes/Artistas/Listas/Videos con conteo, menú "Ordenar" (Título, Artista, Álbum, Año, Añadidas, Más escuchadas, Duración) con dirección, grupos A/B/C… o por año con índice lateral para saltar, lista o cuadrícula, Reproducir/Aleatorio. Álbumes y artistas abren su detalle.
- Inicio simplificado: Sigue escuchando (lo último que sonó), Videos musicales, Añadidas hace poco.
- Reproductor: Canción | Video arriba, tarjeta con "Desde…", título, me gusta y etiquetas (ánimo·BPM, formato, tonalidad, EQ activo, temporizador); Letra · Cola · Info abren un panel que sube desde abajo (letra karaoke con sincronía ±0,5 s y tamaño; cola reordenable con ⋮⋮; info con ecualizador rápido y "Apagar en"). Se arrastra hacia abajo o se toca fuera para cerrar.
- Ajustes es una pestaña: lista de las 7 secciones (3 + 4) con buscador y resumen; cada sección abre su subpágina.
- Barra inferior con 5 pestañas: Inicio, Videos, Buscar, Biblioteca, Ajustes.
- Android: fuera los audios de WhatsApp/Telegram, notas de voz, grabaciones, tonos y alarmas; miniaturas y cuadros de vista previa de los videos (MediaMetadataRetriever / loadThumbnail).
- La onda con marcas de letra NO se usa (decisión del usuario); se mantiene la barra de progreso.
**Por qué:** pedido del usuario: "que se vea todo más ordenado" en el móvil.
**Archivos:** `domain/Library.kt` (LibrarySort, sortedFor, groupFor), `domain/FileNames.kt` (isChatOrVoiceAudio), `screens/LibraryScreen.kt`, `HomeScreen.kt`, `PlayerScreen.kt`, `SettingsScreen.kt`, `VideoScreen.kt`, `components/Rows.kt`, `MobileApp.kt`, `desktop/NowPlaying.kt` (cola e info reutilizadas), `androidMain/platform/AndroidPlatform.kt`.
**Pendiente:** probar en el celular (APK instalado), ecualizador en Android, F5 corrección inteligente de datos.

---

## 2026-10-05 — Media3 en Android, primer APK e instalador de Windows

**Qué:**
- **Android con Media3**: `PlaybackService` (MediaSessionService + ExoPlayer: segundo plano, notificación, pantalla de
  bloqueo, foco de audio, pausa al desconectar auriculares) y `Media3Engine` (MediaController); video con `PlayerView`
  nativo (`NativeVideoView`). Permisos de servicio en primer plano y notificaciones.
- **Primer APK**: `dist/Aurora-0.3.0-debug.apk` (17 MB, `app.aurora.music`, minSdk 26, target 36).
- **Windows**: configuración de `.exe` y `.msi` (ícono, accesos directos, menú Inicio, actualización sobre la versión
  anterior), `build-windows.bat`, `docs/COMPILAR.md` y `dist/aurora-codigo.zip` para copiar a la PC.
- Ícono propio de la app (Android, Windows, Linux y ventana).

**Correcciones para compilar Android:** Gradle 9.5.1 → **9.8.0** (lo exige AGP 9.4); `compileSdk` 36 → **37**
(lo exige Compose 1.12; plataforma `android-37.0`); `ContextCompat` no estaba disponible → ejecutor propio.
jaudiotagger volvía a escribir en el registro en la versión empaquetada → referencia fija al Logger.

**Verificado:** `assembleDebug` correcto; el APK declara permisos, servicio `mediaPlayback` y fuentes; el paquete de
escritorio (`createDistributable`) arranca y escanea la música; 72 pruebas de escritorio en verde.

**Sin verificar todavía:** la app en un celular real (no hay dispositivo conectado) y el `.exe` (se genera en Windows).
Ecualizador en Android pendiente (VLC no se usa allí).

---

## 2026-10-05 — Ajustes por secciones, ecualizador sin saturación y Reproduciendo en tres columnas

Implementación de `files/Ajustes · Aurora.html` y `files/Reproduciendo · Aurora.html`, **sin la onda con marcas**
(pedido del usuario).

**Ajustes**
- 7 secciones en una lista vertical (↑ ↓), buscador que salta a la sección y resalta la fila, panel derecho oculto.
- Etiquetas cortas con pista de una línea y "?" para la explicación larga; formatos como chips.
- Biblioteca: carpetas, contadores, **Vigilar cambios** (detecta archivos nuevos con WatchService y vuelve a buscar),
  **Incluir videos**.
- Apariencia: 7 temas, **Color según la canción** (solo Aurora), **Luces de fondo**, **Densidad** cómoda/compacta.
- Sonido: **ecualizador de 10 bandas + preamplificador** (VLC) con curva en vivo, arrastre y teclado
  (flechas ±0,5, RePág/AvPág ±3, Inicio/Fin, 0), **18 presets** de VLC, **Evitar saturación** y **Normalizar volumen**.
- Reproducción: estado de VLC, **Fundido** 0–12 s, **Sin pausas**, **Recordar dónde quedé**, **Al terminar la cola**.
- Letras: descargar, **preferir .lrc**, letras guardadas (cantidad, tamaño, borrar).
- Accesibilidad: **tamaño del texto** 90–150 %, **letra más grande**, **alto contraste**, **reducir movimiento**,
  **foco reforzado**, **anunciar canciones** (región en vivo para lectores de pantalla) y lista de atajos.
- Acerca de: versión, licencia y licencias de terceros.
- Atajos nuevos: Ctrl+, (Ajustes) y Ctrl+K (buscar).

**Ecualizador: medición de saturación** (VLC escribe la salida a WAV y se cuentan muestras recortadas)
- Los presets de VLC tal cual recortan mucho: Rock 21,8 %, Graves 30 %, Auriculares 33 %.
- En VLC el preamplificador +12 = volumen original; en la app se muestra 0 dB = original.
- "Evitar saturación" baja el preamplificador según: 2,5 × banda grave más alta, 1,5 × aguda más alta y
  1,6 × las 3 bandas vecinas más subidas. Medido con 2 canciones reales: **0 % de recorte** en los presets probados.
  Costo: los presets fuertes suenan más bajos (se avisa cuánto); se puede desactivar.
- Las bandas son las reales de VLC 3: 31, 62, 125, 250, 500 Hz, 1, 2, 4, 8, 16 kHz.

**Reproduciendo (escritorio)**
- Ficha: portada, enlaces a artista/álbum, Me gusta, Lista, Video, Compartir; "Esta canción" (ánimo, BPM, tonalidad,
  formato, kHz, bits, kbps), **veces reproducida y última vez** (se cuentan a los 30 s), ecualizador con curva en
  miniatura y preset ◀ ▶, **Apagar en** 15 / 30 min / 1 h / al terminar la canción.
- Letra: origen, **sincronía ±0,5 s guardada por canción**, **A− / A+**, **Enfoque**, Guardar .lrc; girar la rueda deja
  de seguir la canción y aparece **Volver a la línea actual**.
- Panel derecho: **Cola** (desde qué lista, limpiar, arrastrar el asa para reordenar, quitar con ×),
  **Info** (pista n de m, formato, tamaño, ruta, añadida, veces) con **Abrir carpeta** y **Editar datos**,
  **Artista** (sus canciones y álbumes en la biblioteca y "Mezcla de este artista").
- **Editar datos**: título, artista, álbum, año y género; se guarda en la app y opcionalmente en las etiquetas del
  archivo (jaudiotagger); vuelve a buscar la letra con los datos nuevos.
- Ventana ≥ 1440: tres columnas; 1200–1439: el panel derecho pasa a un botón; < 1200: ficha reducida encima de la letra.

**Correcciones:** `ConcurrentModificationException` en la caché de portadas (podía congelar la interfaz) → cachés
con candado; curva del ecualizador desalineada; cifra de protección que no contaba el límite de VLC; chip cortado.

**Verificado:** 72 pruebas, 0 fallos (ecualizador, preferencias, estadísticas, desfase, cola editable, fin de cola,
reanudar, concurrencia de la caché). Capturas de Ajustes y Reproduciendo a 1920, 1440 y 1100 en `docs/capturas/`.

**Pendiente:** las preferencias de accesibilidad no se aplican todavía a cada animación del móvil; "Normalizar"
empieza a regir desde la siguiente canción (limitación de VLC).

---

## 2026-10-05 — Menú contextual: clic derecho sobre otra pista lo vuelve a abrir

**Qué:** con el menú de clic derecho abierto, otro clic derecho sobre otra canción o video no hacía nada (una capa
invisible tapaba la ventana y solo se cerraba con clic izquierdo). Ahora el menú contextual no lleva capa: la ventana
detecta los clics fuera del menú (`DesktopApp`, pase inicial de puntero) y lo cierra. Con clic derecho el evento sigue
hasta la pista de debajo, que abre su menú en la nueva posición; con clic izquierdo solo se cierra (no activa lo de
abajo, como en el sistema). Esc también lo cierra.

**Verificado:** prueba automática `ContextMenuTest` (clic derecho en una canción → menú; clic derecho en otra → menú de
esa otra; clic izquierdo fuera → cerrado y nada se reproduce; Esc → cerrado). 62 pruebas, 0 fallos.

---

## 2026-10-05 — Clic derecho, ventana de detalles y letra grande en el panel

**Qué:**
- **Menú contextual (clic derecho)** sobre cualquier canción o video: tarjetas de Inicio, tarjetas de video,
  filas y tablas de Biblioteca/Listas/Búsqueda, "A continuación", "Más videos" y la barra de reproducción.
  Aparece junto al cursor (sin salirse de la ventana). Opciones: Reproducir, Reproducir a continuación,
  Añadir a la cola, Me gusta, Añadir a una lista, (en listas propias) mover/quitar, **Ver detalles** y
  Mostrar en la carpeta. El botón ⋯ y el móvil usan la misma lista de opciones en hoja.
- **Ventana de detalles**: portada (o video con su tira de fotogramas), Reproducir / Me gusta / Añadir a una lista,
  y tipo, duración, álbum, artista, año, género, tempo, tonalidad, formato, calidad, tamaño, fecha en que se añadió,
  compositores, origen de la información y ubicación (seleccionable) con "Mostrar en la carpeta".
- **Cola**: "Reproducir a continuación" y "Añadir a la cola" (`PlayerController.enqueue`).
- **Letra grande en el panel "Reproduciendo"**: recuadro alto con la línea anterior, la actual con el relleno del
  karaoke y 3–5 siguientes, que suben con suavidad. El panel crece en pantallas grandes (300 → 350 → 400 dp desde
  1440 y 1700 dp) y la letra con él.

**Correcciones:** el recuadro de letra mezclaba el texto viejo y el nuevo al cambiar de línea → ahora es una lista
que se desplaza; el "♪" activo ahora se resalta.

**Verificado:** clic derecho sobre tarjeta de canción, video y fila de tabla; detalles de una canción y de un video
reales; panel de letra a 1920×1080 y 1280×800. 61 pruebas, 0 fallos.

---

## 2026-10-05 — Letras automáticas en modo karaoke (LRC + LRCLIB)

**Qué:**
- **Descarga automática** de la letra al empezar cada canción. Orden: archivo `.lrc` junto a la canción o etiqueta
  del archivo → caché local → **LRCLIB** (https://lrclib.net, libre y gratuito).
  En LRCLIB: búsqueda exacta (título, artista, álbum, duración) y, si falla, búsqueda por título y artista
  eligiendo la versión **sincronizada** con la duración más parecida (±8 s). Se limpian adornos del título
  ("(Versão Remasterizada)", "(En Vivo…)", "feat."). Reintentos cuando LRCLIB está ocupado (503/429).
- **Formato LRC completo**: varias marcas por línea, milisegundos, `[offset:]` y **LRC mejorado** con tiempos por palabra.
- **Karaoke en movimiento**: la línea activa se rellena con el color del tema mientras se canta (palabra por
  palabra si hay tiempos por palabra; si no, repartido a lo largo de la línea). La posición se interpola por fotograma.
- Estado visible ("Letra sincronizada de LRCLIB", "Buscando la letra…", "Sin letra") y botones **Buscar de nuevo**
  y **Guardar .lrc** (escribe el archivo junto a la canción para usarlo en otros reproductores).
- Caché en `~/.cache/aurora/letras/` (funciona sin conexión). "No encontrada" se reintenta a los 3 días;
  un fallo de red no se guarda como "no encontrada".
- Ajustes → Letras: activar o desactivar la descarga automática (privacidad).

**Verificado:** 15 de 15 canciones reales del usuario con letra sincronizada (0,4–6 s cada una, luego desde caché);
karaoke capturado en dos momentos mostrando el avance del relleno. 60 pruebas, 0 fallos.

**Pendiente:** corrección de título/artista (F5) para archivos sin etiquetas, que hoy no encuentran letra.

---

## 2026-10-05 — Cambio Canción/Video automático y video en segundo plano

**Qué:**
- **Selector "Canción | Video"** en el reproductor (móvil), en "Reproduciendo" y en la vista de video (escritorio).
  "Video" muestra la imagen; "Canción" muestra portada y letra mientras el video **sigue sonando en segundo plano**.
  En canciones sin video aparece "Sin video" deshabilitado.
- **Cambio automático**: con el reproductor abierto, si la cola pasa de una canción a un video se muestra el video,
  y si pasa a una canción se vuelve a portada y letra. Si el usuario eligió "Canción" para un video, los
  siguientes videos no se abren solos (se respeta el segundo plano).
- **Mini video flotante** (picture-in-picture dentro de la app): al navegar por otras secciones con un video sonando
  aparece en la esquina con imagen en vivo, progreso, pausa, ampliar y cerrar (al cerrarlo, el sonido sigue).
  En escritorio solo aparece si el panel derecho está oculto (si no, el video se ve en el panel).

**Verificado (motor VLC real, archivos del usuario):** cola canción → video con el reproductor abierto cambia sola
a video; "Canción" deja el video sonando (posición 11 s → 17 s); en Inicio aparece el mini video; en móvil, cerrar el
video deja el mini video sobre el mini reproductor con el sonido activo. 51 pruebas, 0 fallos.

**Pendiente:** en Android el segundo plano con la pantalla apagada necesita Media3 + MediaSessionService (próxima fase).
En escritorio el sonido sigue con la ventana minimizada (VLC no se detiene).

---

## 2026-10-05 — Listas mixtas (canciones y videos) y vista previa de videos

**Qué:**
- **Listas mixtas**: una lista puede tener canciones y videos en el orden que el usuario quiera.
  Los videos se añaden igual que las canciones (⋯ en la tarjeta del video, en "Más videos", y "Añadir a una lista"
  en la vista del video). En las filas, los videos muestran miniatura 16:9 y la etiqueta VIDEO.
  Al reproducir la lista, la cola pasa de canción a video y viceversa (el video se ve en el panel derecho o con V).
- **Miniaturas reales**: VLC (sin sonido) toma fotogramas al 15, 35, 55 y 75 % de cada video; se reducen a 480 px
  y se guardan en `~/.cache/aurora/miniaturas/` (se regeneran si el archivo cambia).
- **Vista previa**: en escritorio, al pasar el cursor por un video se recorren esos momentos con una barra de
  segmentos; en móvil, cada video de la sección Videos muestra una tira de 4 fotogramas.

**Por qué:** pedido del usuario: combinar audio y video en una lista y reconocer cada video sin abrirlo.

**Archivos:** `desktopMain/.../VideoThumbnailer.kt`, `DesktopMediaSource.kt` (miniatura como portada del video),
`platform/Platform.kt` (`loadPreviewFrames`), `components/Cover.kt` (`PreviewCache`), `components/Common.kt`
(`VideoThumb`, `VideoStoryboard`, `VideoCard`, `VideoBadge`, `RowArt`).

**Verificado:**
- Miniaturas de los videos reales: 4 fotogramas distintos por video, ~1,3 s la primera vez y 6 ms desde la caché.
- Lista mixta creada con 3 canciones y 2 videos: orden respetado y etiquetas VIDEO visibles.
- 51 pruebas, 0 fallos. Capturas en `docs/capturas/`.

---

## 2026-10-05 — Reproductor de video, listas propias y audio sin saturación

**Qué:**
- **Audio sin saturación.** Causa encontrada: el sistema de sonido (WirePlumber) reconocía a Aurora como "VLC"
  y le aplicaba el volumen guardado de la app VLC (195 %); el flujo sonaba al 125 % (+5,8 dB) y recortaba.
  Arreglo: identidad propia ante PulseAudio/PipeWire (`application.name = Aurora`, `application.id = app.aurora`),
  volumen tope 100 % (0 dB, sin ganancia) reaplicado al arrancar cada pista aunque el sistema restaure otro,
  remuestreo speex calidad 10 y sin estiramiento de tiempo. Medido: flujo de Aurora a 100 % / 0,00 dB.
- **Reproductor de video** (escritorio, VLC): los fotogramas se reciben en memoria y se dibujan con Compose,
  así el video toma la forma del tema y lleva controles propios encima (reproducir, progreso, tiempo, HD,
  pantalla completa). Modo cine con panel "Más videos"; pantalla completa real de la ventana (F / Esc);
  "Solo audio" desactiva la imagen sin cortar el sonido; video en miniatura en el panel derecho.
  Móvil: pantalla de video con "Más videos" y botón de video en el mini reproductor.
- Si VLC no puede decodificar un video, la app avisa con el comando para instalar el complemento.
- **Listas propias**: crear (lateral "+", Biblioteca → Listas → "Nueva lista", o desde una canción),
  añadir canciones (⋯ en cualquier fila, botón en la barra de reproducción y en el reproductor),
  quitar, mover arriba/abajo, renombrar, borrar y "guardar como lista propia" una mezcla automática.
  Se guardan junto con **Me gusta** (ya no se pierden al cerrar).
- **Hoja de opciones** de canción (spec §2.6): Me gusta, Añadir a una lista, mover/quitar en listas, Compartir.
- Atajos: no se activan mientras se escribe en un campo; un clic fuera del campo los recupera.

**Por qué:** pedido del usuario: video, crear listas y que el audio dejara de oírse saturado.

**Archivos principales:** `player/MediaEngine.kt` (antes AudioEngine), `VlcEngine.kt`, `DefaultPlayerController.kt`,
`components/Video.kt`, `components/Dialogs.kt`, `data/PlaylistRepository.kt`, `AppState.kt`,
`desktop/DesktopViews.kt` (`DesktopVideo`), `desktop/Chrome.kt` (Más videos), `screens/VideoScreen.kt`.

**Correcciones durante la fase:**
- El VLC del equipo no tenía decodificador H.264 → se instaló `vlc-plugin-ffmpeg` (y la app ahora lo detecta).
- La imagen llegaba con relleno (1920×1090) → se recorta al tamaño real (1920×1080).
- En "Solo audio" se colaba un fotograma tardío → se descarta.
- Enter en el diálogo de nombre llegaba dos veces → solo se procesa al bajar la tecla.
- Espacio dentro del buscador pausaba la música → los atajos se ignoran mientras se escribe.
- Las listas nuevas no aparecían hasta reiniciar la vista → se recalculan al cambiar.

**Verificado:**
- 50 pruebas, 0 fallos (incluye listas, Me gusta, codificación, y reproductor con motor falso).
- Con archivos reales: MP3 a 100 % / 0 dB con identidad "Aurora"; MP4 1080p con fotogramas 1920×1080;
  "Solo audio" sin imagen y sin cortar el sonido; reactivar recupera la imagen.
- Capturas fuera de pantalla de modo cine, pantalla completa, listas, diálogos y móvil → `docs/capturas/`.

**Pendiente:**
- Video y audio real en Android (Media3) e iOS.
- Subtítulos .srt y elegir calidad/pista de audio del video.
- Reordenar listas arrastrando (hoy: "Mover arriba/abajo").

---

## 2026-10-05 — Música real, ajustes de carpetas, 7 temas y versión de escritorio

**Qué:**
- **Ajustes** (avatar o engranaje en móvil; "Ajustes" en el lateral de escritorio):
  carpetas de música y videos (añadir con el selector del sistema o escribiendo la ruta, quitar, volver a buscar),
  selector de **tema** con vista previa real, estado del audio y "Acerca de".
- **Escaneo real**: carpetas y subcarpetas, etiquetas con jaudiotagger (título, artista, álbum, año, género, BPM,
  letra, formato, kbps, portada incrustada), respaldo por nombre de archivo ("Título - Artista (320).mp3"),
  `.lrc` junto a la canción, duración de MP4. Portadas reducidas a 512 px en caché.
- **Audio real en escritorio con VLC** (vlcj): reproducir, pausar, saltar, volumen guardado, siguiente al terminar.
- **7 temas**: Aurora, Póster, Pétalo, Seda, Carbono, Estadio y Bruma, con sus colores, formas (arco, círculo,
  esquinas cortadas, paralelogramo), 13 fuentes OFL, decoraciones de portada y de fondo. Se guarda el tema elegido.
- **Interfaz según el dispositivo** (`FormFactorRules`): en PC, diseño de escritorio de 3 columnas (lateral 232,
  panel 300, barra 86) con tamaños de letra de escritorio; en móvil, diseño de teléfono. Ventana 1280×800,
  mínimo 960×600; panel flotante entre 1024 y 1279 dp; lateral de íconos bajo 1024 dp. Atajos de teclado.
- **Mezclas automáticas** para la biblioteca real: Añadidas hace poco, Descubrimientos, Lo mejor de (artistas), Me gusta.
- Android: escaneo con MediaStore, ajustes con SharedPreferences y petición de permisos (sin compilar: falta SDK).

**Por qué:** pedido del usuario: encontrar su música, elegir carpetas, corregir que en PC todo se veía grande
y desproporcionado (antes se usaba el diseño de teléfono estirado) y añadir los 7 temas de `files/`.

**Archivos principales:** `App.kt`, `AppState.kt`, `MobileApp.kt`, `desktop/*`, `theme/*`, `platform/*`,
`data/SettingsRepository.kt`, `data/LibraryRepository.kt`, `player/DefaultPlayerController.kt`,
`desktopMain/.../DesktopMediaSource.kt`, `VlcAudioEngine.kt`, `Mp4.kt`, `screens/SettingsScreen.kt`.

**Correcciones durante la fase:**
- Portadas incrustadas de 1200×1200 llenaban la memoria → se reducen a 512 px.
- Videos con duración 0 → lector de la caja `mvhd` de MP4.
- Barra de pestañas transparente dejaba ver el contenido → fondo opaco.
- Chips de orden cortados en escritorio → fila desplazable.

**Verificado:**
- 43 pruebas, 0 fallos (`./gradlew :composeApp:desktopTest`).
- Escaneo de `~/Music` y `~/Videos` de este equipo: 156 canciones con etiquetas y portada + 10 videos en < 1 s.
- VLC reproduce un MP3 real (posición avanza, el salto funciona).
- Capturas fuera de pantalla de los 7 temas en escritorio (Inicio y Reproduciendo) y móvil (Inicio y Reproductor),
  y de la biblioteca real a 1280, 1100 y 980 px → `docs/capturas/`.

**Pendiente / sin verificar:**
- Reproducción de **video** (se listan, pero al tocarlos se avisa que llega en la próxima fase).
- Android sin compilar (sin SDK); audio real en Android (Media3) e iOS pendientes.
- Las listas propias (crear/añadir) y "Me gusta" aún no se guardan al cerrar.

---

## 2026-10-05 — Fase 1: documentación y esqueleto del proyecto

**Qué:**
- Se eligió **Kotlin Multiplatform + Compose Multiplatform** (Android, escritorio, iOS) y licencia **GPL-3.0**.
- Se crearon `ARQUITECTURA.md`, `BITACORA.md`, `README.md` y `LICENSE`.
- Proyecto Gradle: Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.4.1, Gradle 9.5.1, JDK 21.
- Tema Aurora (spec §4–6): paleta, medidas, curvas de animación, fuentes Syne y DM Sans (OFL, incluidas en el proyecto).
- Sistema de color (spec §3): `moodFromBpm`, `extractAccent`, `buildPalette`; colores animados 1,2 s.
- Componentes: fondo con 3 luces animadas por tempo (se detienen en pausa), vidrio, portadas, botones con
  escala de pulsación, barra de progreso táctil, ecualizador, filas, mini reproductor, pestañas, avisos, íconos propios.
- Pantallas: Inicio, Buscar, Biblioteca (Canciones / Álbumes / Artistas / Géneros / Listas, orden por título, año
  y fecha de añadido), **Videos (sección aparte)**, Lista, Reproductor y **Letra karaoke** sincronizada.
- Dominio: modelos, `MediaType.fromFileName` (separa audio de video), agrupación/orden de biblioteca,
  lector de letras LRC (`parseLrc`), reglas de cola (`QueueRules`).
- Reproductor: interfaz `PlayerController` con `expect/actual`; por ahora un reproductor **simulado** en todas las plataformas.
- 26 pruebas unitarias (color, biblioteca, LRC, cola).

**Por qué:** dejar la base y el diseño listos antes de conectar la reproducción real.

**Decisiones:**
- PixelPlayer se usa solo como inspiración: su licencia es propietaria desde 2026-05-12 (ADR-2).
- Sin Material Design para una estética única (ADR-4).
- El objetivo Android se activa solo si hay SDK, para poder compilar el escritorio sin él (ADR-5).
- Navegación con estado simple en `App.kt`; se migrará a `navigation-compose` en F3.

**Correcciones durante la fase:**
- La letra no se desplazaba a la línea activa: ahora el relleno superior es el 36 % del alto y se desplaza al ítem activo.
- El chip seleccionado perdía el fondo tras tocarlo: la escala de pulsación ahora va por fuera del recorte y del fondo.

**Verificado:**
- `./gradlew :composeApp:desktopTest` → 26 pruebas, 0 fallos.
- Interfaz renderizada fuera de pantalla (Inicio, Reproductor, Letra, Biblioteca, Videos) → capturas en `docs/capturas/`.

**Pendiente / sin verificar:**
- **Android no se compiló**: esta máquina no tiene SDK de Android. Instalar Android Studio (o el SDK) y
  ejecutar `./gradlew :androidApp:assembleDebug`.
- **iOS no se compiló**: requiere una Mac con Xcode (ver `iosApp/README.md`).
- Fase 2: escaneo real y reproducción nativa de audio y video.
