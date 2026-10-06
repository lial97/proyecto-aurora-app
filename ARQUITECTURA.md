# Arquitectura de Aurora

> Documento vivo. Cada cambio de diseño se refleja aquí y se registra en [BITACORA.md](BITACORA.md).

## 1. Visión

Aurora es un reproductor de **música y video** local, **gratuito y de código abierto (GPL-3.0)**,
para **Android, escritorio (Linux, Windows, macOS) e iOS**, con una interfaz propia: oscura,
de vidrio y con luces de color que cambian con cada canción.

Referencias de diseño: `files/aurora-app-musica.html` (prototipo) y
`files/aurora-especificaciones.txt` (especificación de interfaz, color, animación e interacción).
Cuando este documento cita "spec §N" se refiere a ese archivo.

## 2. Requisitos

### Funcionales

| # | Requisito | Fase |
|---|---|---|
| RF1 | Reproducir audio: mp3, flac, m4a/aac, ogg/opus, wav, wma, aiff | ✅ escritorio (VLC) / F2 Android, iOS |
| RF2 | Reproducir video: mp4, mkv, webm, mov, avi, 3gp | ✅ escritorio (VLC) / F2 Android, iOS |
| RF3 | Audio y video en **secciones separadas** (Música y Videos), nunca mezclados | F1 ✅ (modelo) / F2 |
| RF4 | Listas de reproducción: crear, renombrar, añadir/quitar y reordenar canciones | ✅ (se guardan) |
| RF5 | Biblioteca por **Canciones, Álbumes, Artistas, Géneros y Listas** | ✅ con archivos reales |
| RF6 | Ordenar por **título, año y fecha de añadido** | F1 ✅ |
| RF7 | **Letra sincronizada tipo karaoke**, descargada automáticamente | ✅ (LRCLIB + .lrc, relleno en movimiento) |
| RF8 | **Corrección inteligente de metadatos**: `00000.mp3` → preguntar título y artista → buscar en la web → actualizar título, artista, álbum, año, género, portada y letra | F5 |
| RF9 | Color dinámico: atmósfera por tempo y énfasis por portada (spec §3) | F1 ✅ |
| RF10 | Cola, aleatorio, repetir, anterior/siguiente con las reglas de la spec §8 | ✅ |
| RF11 | **Ajustes con carpetas** donde buscar música y videos (añadir, quitar, volver a buscar) | ✅ |
| RF12 | **7 temas**: Aurora, Póster, Pétalo, Seda, Carbono, Estadio, Bruma (`files/TEMAS_VIDEO_ESCRITORIO.md`) | ✅ |
| RF13 | **Interfaz de escritorio** de 3 columnas en PC y de teléfono en móvil (`files/escritorio-app-musica.html`) | ✅ |

### No funcionales

- **Multiplataforma** con un solo lenguaje: Kotlin.
- **Sin conexión primero**: todo funciona sin internet; la red solo enriquece (letras, portadas, metadatos).
- **Privacidad**: no hay cuentas ni analítica; las búsquedas en la web solo envían título/artista.
- **Accesibilidad**: spec §10 (etiquetas, foco visible, reducir movimiento).
- **Idioma**: español primero; textos preparados para traducirse.
- **Licencia**: GPL-3.0. Sin código copiado de proyectos con licencias incompatibles (ver §11).

## 3. Tecnología

**Kotlin Multiplatform (KMP) + Compose Multiplatform.** Interfaz y lógica compartidas en `commonMain`;
solo el motor de reproducción, el escáner de archivos y la escritura de etiquetas son específicos de cada plataforma.

| Capa | Elección | Estado |
|---|---|---|
| Lenguaje | Kotlin 2.4 | ✅ |
| UI | Compose Multiplatform 1.12 (foundation, sin Material: tema propio) | ✅ |
| Asincronía | kotlinx.coroutines + StateFlow | ✅ |
| Fechas | kotlinx-datetime | ✅ |
| Base de datos | Room KMP (SQLite) | F2 |
| Inyección de dependencias | Koin | F2 |
| Red | Ktor Client + kotlinx.serialization | F4 |
| Imágenes | Coil 3 | F2 |
| Reproducción Android | Media3 ExoPlayer + MediaSessionService | F2 |
| Reproducción escritorio | vlcj 4.12 (requiere VLC instalado) | ✅ |
| Reproducción iOS | AVPlayer | F2 |
| Etiquetas (leer) | jaudiotagger 3.0 en escritorio; MediaStore en Android | ✅ |
| Etiquetas (escribir) | jaudiotagger | F5 |
| Pruebas | kotlin.test | ✅ |

### ¿Por qué no Flutter, Python o Java?

- **Flutter** usa Dart (descartado por el autor).
- **Python** (Kivy, BeeWare): interfaz poco moderna, APK pesados, mala integración con Play Store. Flet usa Flutter por debajo.
- **Java** no tiene un framework de UI móvil multiplataforma moderno.
- **KMP + Compose** es nativo en Android, comparte la UI en escritorio e iOS, y Kotlin es el lenguaje oficial de Android.

## 4. Capas

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Compose)        screens/  components/  theme/            │
│   ↓ eventos   ↑ estado (StateFlow)                           │
│ Estado              player/PlayerController, ViewModels (F2) │
│   ↓                                                          │
│ Dominio (puro)      domain/  color/   ← sin dependencias de  │
│                                         plataforma, con tests│
│   ↓                                                          │
│ Datos               data/ repositorios → BD, archivos, web   │
│   ↓                                                          │
│ Plataforma          expect/actual: reproductor, escáner,     │
│                     etiquetas, permisos                      │
└──────────────────────────────────────────────────────────────┘
```

Reglas:
1. `domain/` y `color/` no importan nada de UI ni de plataforma (solo `Color` de Compose en `Palette.kt`).
2. La UI nunca llama a la red ni a la BD directamente: pasa por repositorios.
3. Lo específico de plataforma se declara con `expect` en `commonMain` y se implementa con `actual`.

## 5. Estructura del proyecto

```
app-music/
├── ARQUITECTURA.md · BITACORA.md · README.md · LICENSE (GPL-3.0)
├── files/                       diseños y especificaciones (referencia, no se modifican)
├── docs/capturas/               capturas de la interfaz (7 temas, móvil y escritorio)
├── docs/licencias/              licencias de las fuentes (OFL)
├── gradle/libs.versions.toml    versiones de todas las dependencias
├── composeApp/src/
│   ├── commonMain/kotlin/app/aurora/
│   │   ├── App.kt               raíz: decide móvil o escritorio (FormFactorRules), paleta, tema
│   │   ├── AppState.kt          estado compartido: ajustes, biblioteca, reproductor, avisos, Me gusta
│   │   ├── MobileApp.kt         interfaz de teléfono (pestañas + pantallas)
│   │   ├── desktop/             interfaz de PC: DesktopApp (estructura, atajos), Chrome (lateral,
│   │   │                        barra superior, barra de reproducción, panel derecho), DesktopViews
│   │   ├── theme/               AppTheme (modelo), Themes (los 7), Shapes, Theme (proveedor, tipografía), Tokens
│   │   ├── color/               Mood, ExtractAccent, Palette (color dinámico de Aurora)
│   │   ├── components/          Cover (portadas y decoración), Backdrop (fondos), Controls, Rows, Common, Icons
│   │   ├── screens/             pantallas móviles + Ajustes y Letra (compartidas con escritorio)
│   │   ├── domain/              modelos, biblioteca (agrupar/ordenar), LRC, nombres de archivo, mezclas
│   │   ├── data/                SettingsRepository (persistente), LibraryRepository (escaneo), sample/
│   │   ├── platform/            interfaces: KeyValueStore, MediaSource, PlatformServices (expect)
│   │   └── player/              PlayerController, DefaultPlayerController (cola), AudioEngine, QueueRules
│   ├── commonMain/composeResources/font/   13 fuentes de los 7 temas (OFL)
│   ├── commonTest/              pruebas: color, biblioteca, LRC, cola, nombres, mezclas, ajustes, temas
│   ├── desktopMain/             Main.kt (ventana 1280×800, mínimo 960×600), DesktopPlatform (ajustes en
│   │                            archivo), DesktopMediaSource (escaneo + jaudiotagger), Mp4, VlcAudioEngine
│   ├── androidMain/             AndroidPlatform: SharedPreferences + MediaStore
│   └── iosMain/                 MainViewController + plataforma mínima (pendiente)
├── androidApp/                  MainActivity (pide permisos de audio y video), manifiesto
└── iosApp/                      instrucciones para crear el proyecto Xcode en una Mac
```

El módulo `androidApp` y el objetivo Android de `composeApp` solo se activan si hay un SDK de Android
(`local.properties` → `sdk.dir`, o `ANDROID_HOME`). Así el escritorio compila en cualquier máquina.

### 5.1 Móvil o escritorio (`FormFactorRules` en `App.kt`)

| Dispositivo | Ancho | Interfaz |
|---|---|---|
| PC | ≥ 720 dp | Escritorio (lateral, contenido, panel derecho, barra de reproducción) |
| PC | < 720 dp | Teléfono (ventana muy estrecha) |
| Móvil | < 1000 dp | Teléfono |
| Móvil (tableta horizontal) | ≥ 1000 dp y alto ≥ 600 | Escritorio |

Dentro del escritorio (`DesktopApp`): ≥ 1280 dp tres columnas; 1024–1279 el panel derecho flota y se abre
con su botón; < 1024 el lateral se reduce a íconos (72 dp). Los tamaños de letra son distintos en móvil y
escritorio (`TypeScale.h1` / `h1Desktop`, etc.).

### 5.2 Temas

`AppTheme` reúne colores, formas, fuentes, tamaños y rasgos (decoración de portada, fondo, panel del
reproductor, mayúsculas, etiquetas técnicas). **Ningún componente usa colores, formas o fuentes fijas**:
todo se lee con `Ui.colors`, `Ui.type`, `Ui.shapes` y `Ui.theme`. Solo Aurora tiene color dinámico
(fondo y énfasis por canción). El tema se elige en Ajustes, se aplica con transición de 600 ms y se guarda.

## 6. Modelo de datos

Basado en spec §9, ampliado para archivos reales:

```kotlin
Track(
  id, title, artist, album, durationSec,
  mediaType: AUDIO | VIDEO,          // separa Música de Videos
  bpm?, key?, genre?, year?, releaseDate?,
  dateAddedMs,                       // para "fecha de añadido"
  filePath?, coverUri?,              // archivo y portada reales
  coverRecipe?,                      // solo datos de ejemplo
  composers?, producers?, mixing?, monthlyListeners?,
  lyrics: List<LyricLine>,           // LyricLine(startSec, text); "♪" = instrumental
  metadataSource: FILE_TAGS | USER | MUSICBRAINZ,
)
Playlist(id, name, trackIds, colors, description?)
PlaybackState(queue, index, positionSec, isPlaying, shuffle, repeatOne)
```

En F2 se crean las tablas Room: `track`, `playlist`, `playlist_track (playlistId, trackId, position)`,
`lyrics (trackId, lrc, source)`, `cover_accent (trackId, h, s, l)` (énfasis calculado una vez y guardado, spec §11).

## 7. Flujos principales

### 7.1 Escaneo de la biblioteca ✅
1. **Carpetas**: Ajustes → "Carpetas de música y videos". En escritorio, por defecto `xdg-user-dir MUSIC/VIDEOS`
   (p. ej. `~/Music`, `~/Videos`); se añaden con el selector del sistema (zenity, o el de Java si no está)
   o escribiendo la ruta. En Android, vacío = todo el dispositivo (MediaStore), y se pueden elegir carpetas detectadas.
2. Se recorren las carpetas y subcarpetas (se omiten ocultas, `node_modules`, `build`, `target`).
3. `MediaType.fromFileName()` decide si va a **Biblioteca** (audio) o a **Videos**.
4. jaudiotagger lee título, artista, álbum, año, género, BPM, letra, formato, kbps y si hay portada incrustada.
   Sin etiquetas, `guessFromFileName()` deduce "Título - Artista (320).mp3". Un `.lrc` junto al archivo da letra karaoke.
   La duración de los videos MP4 se lee de la caja `mvhd` (`Mp4.kt`).
5. Las portadas se cargan bajo demanda, se reducen a 512 px y se guardan en una caché de 80 (`CoverCache`).
6. Ajustes guardados en `~/.config/aurora/ajustes.properties` (Linux), `%APPDATA%\Aurora` (Windows),
   `~/Library/Application Support/Aurora` (macOS), SharedPreferences (Android).
7. Sin archivos propios se muestra el catálogo de ejemplo con un aviso para ir a Ajustes.

Medido en este equipo: 166 archivos (156 MP3 + 10 MP4) en 0,4–0,7 s.

### 7.1.2 Calidad de audio
- Aurora se identifica ante PulseAudio/PipeWire como `Aurora` / `app.aurora`: el sistema no le aplica el volumen
  guardado de la app VLC (que puede pasar del 100 % y saturar).
- El volumen de la app es 0–100 % y **nunca amplifica** (100 % = 0 dB). Se reaplica al empezar cada pista,
  porque el sistema puede restaurar otro valor al crear el flujo.
- VLC: `--speex-resampler-quality=10`, `--no-audio-time-stretch`.
- Si aún se oye mal, revisar efectos del sistema (p. ej. EasyEffects) y el volumen del flujo "Aurora" en el mezclador.

### 7.1.3 Video
`MediaEngine.video` entrega fotogramas (`VideoOutput.frame`); en escritorio VLC decodifica a memoria (RV32) y
`VideoSurface` los dibuja con Compose (forma del tema, controles encima). Audio y video son la misma reproducción:
"Solo audio" solo desactiva la pista de imagen. Solo se copian fotogramas mientras hay una superficie visible.
Requiere los decodificadores de VLC (en Arch: `vlc-plugin-ffmpeg`); si faltan, la app lo avisa.

### 7.1.7 Preferencias, ecualizador y estadísticas
- `PrefsRepository` (`data/Prefs.kt`): todas las opciones de Ajustes (claves `pref.*`), aplicadas al instante desde
  `AppState` (motor, letras, biblioteca) y desde `LocalPrefs` (densidad, tamaño de texto, contraste, movimiento, foco).
- `audio/Equalizer.kt`: 18 presets de VLC, `EqSettings` con protección contra saturación medida (ver BITACORA).
  `VlcEngine.setEqualizer()` aplica el preamplificador en la escala de VLC (+12 = original).
- `PlayStatsRepository` (veces/última vez), `LyricsOffsetRepository` (desfase por canción), `EditsRepository`
  (correcciones de datos aplicadas tras cada escaneo).
- Reproductor: fundido (rampa de volumen), sin pausas (pasa a la siguiente 0,3 s antes), fin de cola (detener/repetir),
  cola editable (`moveInQueue`, `removeFromQueue`, `clearUpcoming`) y `restore()` para "Recordar dónde quedé".
- `desktop/NowPlaying.kt`: Reproduciendo en tres columnas (ficha, letra, Cola/Info/Artista).

### 7.1.6 Canción / Video y segundo plano
La imagen es opcional y la reproducción es una sola. El selector "Canción | Video" solo cambia qué se muestra.
Reglas (`DesktopApp` y `MobileApp`, al cambiar la pista actual): reproductor abierto + llega un video → vista de video
(salvo `AppState.videoInBackground`); llega una canción estando en el video → portada y letra. Fuera del reproductor,
`PipVideo` muestra el video flotante (`AppState.pipClosedFor` recuerda si se cerró para esa pista).
Los fotogramas solo se copian mientras alguna superficie está visible, así el segundo plano no gasta CPU de más.

### 7.1.5 Miniaturas y vista previa de videos
`MediaSource.loadPreviewFrames()` devuelve varios fotogramas del video; el primero es su portada (`coverUri = video-thumb`).
En escritorio `VideoThumbnailer` usa una instancia de VLC sin audio, toma fotogramas al 15/35/55/75 %, los guarda como
JPEG de 480 px en la caché (clave = ruta + tamaño + fecha) y los carga desde ahí las veces siguientes.
`VideoThumb` los recorre al pasar el cursor; `VideoStoryboard` los muestra en tira (móvil).

### 7.1.4 Listas y Me gusta
`PlaylistRepository` guarda las listas del usuario y los Me gusta en el mismo almacén de ajustes
(formato de texto con escapes, probado con rutas y nombres con tabuladores, saltos y %). Una lista puede
mezclar canciones y videos; la cola los reproduce en orden con el mismo motor. Las mezclas
automáticas no se guardan: se recalculan; se pueden convertir en lista propia.

### 7.1.1 Reproducción
`DefaultPlayerController` maneja la cola (reglas de spec §8) y usa el `AudioEngine` de la plataforma:
VLC en escritorio (`VlcAudioEngine`), pendiente Media3 en Android y AVPlayer en iOS. Las pistas sin archivo
(catálogo de ejemplo) o sin motor avanzan con un reloj simulado. El volumen se guarda.

### 7.2 Letras sincronizadas ✅
`LyricsRepository` (common) + `HttpClient` y `TextCache` de cada plataforma.
1. Archivo `.lrc` con el mismo nombre o etiqueta LYRICS → se usa tal cual.
2. Caché local (`~/.cache/aurora/letras/`, clave = artista|título|duración).
3. LRCLIB `GET /api/get?track_name&artist_name&album_name&duration`; si no hay sincronizada,
   `GET /api/search?track_name&artist_name` y `?q=` → `pickBest()` (sincronizada, ±8 s, título parecido).
   User-Agent propio; reintentos con espera ante 503/429.
4. `parseLrc()` → `LyricLine(startMs, text, words)`; `sungChars()` calcula el relleno del karaoke.
5. `LyricsView` interpola la posición por fotograma (`rememberSmoothPositionMs`) y solo recompone la línea activa.
6. Ajuste "Descargar letras automáticamente" (clave `letras_auto`).

### 7.3 Corrección inteligente de metadatos (F5)
1. **Detectar** pistas sospechosas: título vacío, igual al nombre de archivo, solo números (`00000`), o artista "Unknown/Desconocido".
2. **Preguntar** en un diálogo: "¿Cómo se llama esta canción y quién la canta?" (con sugerencia sacada del nombre del archivo).
3. **Buscar** en MusicBrainz (`/ws/2/recording?query=recording:"…" AND artist:"…"&fmt=json`, User-Agent propio, máx. 1 petición/s).
4. **Mostrar candidatos** (título, artista, álbum, año, portada de Cover Art Archive) y que el usuario confirme.
5. **Aplicar**: actualizar BD, descargar portada, buscar letra (7.2), y **opcionalmente escribir las etiquetas en el archivo** (jaudiotagger).
6. Más adelante: **AcoustID + Chromaprint** para reconocer la canción por su huella de audio sin preguntar.

### 7.4 Color dinámico (✅)
`buildPalette(bpm, extractAccent(portada48x48))` → `AuroraTheme` anima los 5 colores en 1,2 s →
`AmbientBackground` mueve las 3 luces con velocidad `--spd` y se detiene en pausa.

## 8. Navegación

**Móvil**: pestañas Inicio · Videos · Buscar · Biblioteca. Encima se abren Lista, Ajustes (desde el avatar
o el engranaje) y Reproductor/Letra (pantalla completa, ocultan mini reproductor y pestañas).

**Escritorio**: lateral (Inicio, Videos, Buscar, Biblioteca, Ajustes, Tus listas), historial con Atrás/Adelante,
vistas Inicio, Biblioteca (tablas), Lista, Reproduciendo (portada + letra), Videos, Buscar y Ajustes.
Atajos: Espacio, ← →  (±5 s), Ctrl + ← → (anterior/siguiente), L (letra), V (video), F (pantalla completa), Esc (salir).
Los atajos se ignoran mientras se escribe en un campo de texto.
Clic derecho sobre cualquier pista: `Modifier.trackContextMenu()` (usa `LocalTrackMenu`) abre `AppDialog.TrackMenu`
con la posición del cursor; `AnchoredLayout` lo coloca sin salirse de la ventana. "Ver detalles" abre `AppDialog.TrackDetails`.
El panel derecho mide 300/350/400 dp según el ancho de la ventana.

## 9. Permisos y privacidad

| Plataforma | Permiso | Para qué |
|---|---|---|
| Android 13+ | `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO` | Leer la biblioteca |
| Android ≤ 12 | `READ_EXTERNAL_STORAGE` | Leer la biblioteca |
| Android | `INTERNET` | Letras, portadas y metadatos |
| Android (F2) | `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` | Reproducir en segundo plano |

Play Store exige una **política de privacidad** pública (se publicará en el repositorio, F7).

## 10. Hoja de ruta

| Fase | Contenido |
|---|---|
| **F1** ✅ | Documentos, proyecto KMP, sistema de color con pruebas, pantallas, reproductor simulado |
| **F1.5** ✅ | Ajustes con carpetas, escaneo real (escritorio y Android), audio real con VLC en escritorio, 7 temas, interfaz de escritorio de 3 columnas, mezclas automáticas |
| **F1.6** ✅ | Reproductor de video en escritorio (modo cine, pantalla completa, solo audio), listas propias y Me gusta guardados, audio sin saturación |
| **F2** | Media3 + MediaSession en Android (audio y video), AVPlayer en iOS; BD Room con caché del escaneo; subtítulos |
| **F3** | Información de la pista, hoja de opciones (spec §2.6), cola editable, `navigation-compose` |
| **F4** ✅ | Letras automáticas (LRCLIB), caché sin conexión, karaoke con relleno |
| **F5** | Corrección inteligente de metadatos (MusicBrainz + Cover Art Archive), escritura de etiquetas |
| **F6** | Ecualizador, temporizador, gapless/crossfade, widgets, Android Auto, subtítulos, PiP |
| **F7** | Publicación: Play Store, F-Droid, paquetes de escritorio, App Store; CI |

## 11. Decisiones (ADR breves)

**ADR-1 · KMP + Compose Multiplatform.** Ver §3.

**ADR-2 · PixelPlayer solo como inspiración.** PixelPlayer (Kotlin + Compose + Media3) cambió a licencia
**propietaria** el 2026-05-12; solo lo aportado antes conserva MIT. Para evitar riesgos **no se copia su código**:
se toman ideas de funciones (LRCLIB, editor de etiquetas, Media3) y se implementan desde cero.

**ADR-3 · GPL-3.0.** Garantiza que cualquier derivado siga siendo abierto. Es compatible con Play Store.
Todas las dependencias elegidas (Apache-2.0, MIT, LGPL, OFL) son compatibles con GPL-3.0.
Nota: vlcj es GPL-3.0 y libVLC LGPL-2.1, compatibles.

**ADR-4 · Sin Material Design.** Se usa `compose.foundation` con tema, íconos y componentes propios para
lograr una estética única (vidrio + luces), sin la apariencia "Material You" ni la de PixelPlayer.

**ADR-5 · Objetivo Android condicional.** Ver §5. Permite desarrollar y probar en escritorio sin el SDK.

**ADR-7 · Reproductor común con motor por plataforma.** La cola y sus reglas viven en `commonMain`
(`DefaultPlayerController`); cada plataforma solo aporta un `AudioEngine` pequeño. Así Media3/AVPlayer se
añaden sin tocar la interfaz.

**ADR-9 · Video dibujado por Compose.** En lugar de incrustar una ventana nativa de VLC (`SwingPanel`), los
fotogramas se copian a memoria y se dibujan como imagen. Cuesta algo más de CPU, pero permite esquinas y bordes
del tema, controles encima del video y funciona igual en X11 y Wayland.

**ADR-8 · Sin base de datos todavía.** El escaneo completo tarda menos de 1 s con 166 archivos, así que por
ahora se escanea al abrir. Con bibliotecas grandes (miles de archivos) se añadirá Room en F2 como caché.

**ADR-6 · Desenfoque.** Compose Multiplatform no tiene `backdrop-filter`. Las luces se dibujan con degradados
radiales (mismo efecto que un círculo desenfocado, y más barato). El "vidrio" es blanco al 10 % + borde.
Se puede añadir desenfoque real con la librería *Haze* en F3 si hace falta.

## 12. Calidad

- `./gradlew :composeApp:desktopTest` — pruebas de color, biblioteca, letras y cola (deben pasar siempre).
- `./gradlew :composeApp:run` — app de escritorio.
- `./gradlew :androidApp:assembleDebug` — APK (requiere SDK de Android).
- Pendiente F7: GitHub Actions (pruebas + APK + paquetes de escritorio en cada push).
