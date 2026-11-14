# Rendimiento de Aurora

Mediciones antes y después de cada cambio, para que la app no se vuelva más pesada. **Lo más nuevo abajo.**

## Cómo medir

- **Escritorio:**
  - Comando: `AURORA_CPU_PROBE=1 AURORA_FPS=1 AURORA_MUSIC=~/Music ./gradlew :composeApp:desktopTest --tests app.aurora.CpuProbe --rerun -i | grep -E "PROBE|FPS"`
  - Abre una ventana real de 1280×800 unos 60 s.
- **Android:**
  - Comando: `bash scripts/medir-android.sh arranque | memoria <etiqueta> | cpu <s> | cuadros <s>`
  - Usar siempre el APK de **release** (`:androidApp:assembleRelease`).
  - Cuadros por segundo en reposo: `adb shell dumpsys gfxinfo app.aurora.music reset`, esperar 10 s y leer "Total frames rendered".

## Línea base v0.3.1+ (2026-11-14, commit `ca5523c`)

**Android: Samsung A35, Android 16, pantalla de 120 Hz, tema con fondo de cuadrícula (fijo)**

| Medición | Valor |
|---|---|
| Arranque en frío (media de 5) | 448 ms (383–497) |
| Memoria en Inicio (PSS) | 163–166 MB |
| CPU en Inicio, sin sonar | 0 % |
| CPU en Inicio, sonando | **77–82 % de un núcleo** |
| Cuadros en Inicio, sonando y sin tocar | **~64 por segundo** (639 en 10 s) |
| CPU en el reproductor, sonando | **93 % de un núcleo** |
| Cuadros en el reproductor, sonando | **~116 por segundo** (1742 en 15 s) |
| CPU en pausa | 0 % (0 cuadros) |
| Desplazar Inicio | 4,5 % de cuadros lentos; p50 9 ms, p90 11 ms, p99 12 ms |
| Vista de video (1080p 60 fps), sonando | 72 % de un núcleo, ~77 cuadros/s, 174 MB |
| Mini video de la app + mini reproductor (pestaña Videos) | **92 % de un núcleo**, ~78 cuadros/s |

Según `top -H`, con música en Inicio el reparto es: `RenderThread` 32 %, hilo principal 14 % y controlador de la GPU (mali) ~10 %. ExoPlayer apenas gasta. Casi todo el gasto es redibujar sin necesidad:
- `Backdrop.kt` lee el reloj de animación en cada dibujo, **aunque el tema tenga un fondo fijo**. Mientras suena, toda la pantalla se redibuja a 60 fps sin que cambie nada.
- En el reproductor, además, la onda de la barra va a la frecuencia de la pantalla (120 Hz).
- El estado de reproducción entero cambia 4 veces por segundo (posición) y recompone toda la app.

**Escritorio: Linux, OpenGL, ritmo de animación de 33 ms**

| Pantalla | Proceso | Ventana + dibujo | FPS |
|---|---|---|---|
| Inicio, sonando | 23,7 % | 8,4 % | 37 |
| Reproductor, sonando | 18,5 % | 8,5 % | 37 |
| Pausa | 0,1 % | 0,0 % | — |

## Fase 2 de la 0.4: menos redibujo (2026-11-14)

Cambios:
- **Fondo:** el reloj solo corre en los temas cuyo fondo se mueve (Aurora, Póster, Seda y Acuarela). El fondo va en su propia capa.
- **Posición:** sale del estado de reproducción (`LocalPositionMs`). La leen solo la barra, los tiempos, los mini reproductores, el video, la letra y la cinta de Casete, y casi siempre al dibujar.
- **Barra de progreso:** sigue el ritmo de las animaciones (`animatePacedFloat`) en todas las plataformas, no los 120 Hz de la pantalla.
- **Android:** las animaciones continuas van a ~30 fps (antes 60).
- La barra, la línea del mini reproductor, las barras del video y la cinta de Casete tienen capa propia.

**Android (A35, mismo tema de cuadrícula)**

| Medición | Antes | Después |
|---|---|---|
| Inicio, sonando | 77–82 % · ~64 cuadros/s | **19 % · ~4 cuadros/s** |
| Reproductor, sonando (con la onda) | 93 % · ~116 cuadros/s | **57 % · ~31 cuadros/s** |
| Vista de video (1080p 60 fps) | 72 % · ~77 cuadros/s | 71 % · ~29 cuadros/s |
| Mini video + mini reproductor | 92 % · ~78 cuadros/s | **72 % · ~29 cuadros/s** |
| Memoria (reproductor / mini video) | 163 / 174 MB | 155 / 183 MB |
| En pausa | 0 % | 0 % |

En el reproductor, lo que queda es la onda a 30 fps: cada cuadro compone la ventana entera en la GPU. "Reducir movimiento" la apaga. El video depende sobre todo de decodificarlo y mostrarlo (`MediaCodec`, `TextureView`).

**Escritorio (Linux, tema Aurora con luces animadas)**

| Pantalla | Antes | Después |
|---|---|---|
| Inicio, sonando | 23,7 % · 37 FPS | **19,8 % · 34 FPS** |
| Reproductor, sonando | 18,5 % · 37 FPS | **17,3 % · 34 FPS** |
| Pausa | 0,1 % | 0,1 % |

## Fase 5 de la 0.4: video en la ventanita del sistema (2026-11-14)

| Medición (A35) | Valor |
|---|---|
| Ventanita de imagen en imagen con el video 1080p de 60 fps | 71 % de un núcleo · ~28 cuadros/s (igual que la vista de video) |

En segundo plano sin ventanita (PiP apagado en los ajustes del sistema, o pantalla apagada con la app delante), la imagen del video se apaga y solo se decodifica el audio. Falta medirlo: requiere bloquear el teléfono.

## Final de la 0.4.0 (2026-11-14)

**Android (A35, APK de release 0.4.0, tema Carbono)**

| Medición | Línea base 0.3.1 | 0.4.0 |
|---|---|---|
| Arranque en frío (media de 5) | 448 ms | **348 ms** (219–459) |
| Memoria en Inicio | 166 MB | 161 MB |
| Inicio, sonando | 77–82 % · ~64 cuadros/s | **13 % · ~4 cuadros/s** |
| Reproductor, sonando | 93 % · ~116 cuadros/s | **30 % · ~31 cuadros/s** |
| Mini video + mini reproductor (ahora solo el mini video) | 92 % | 72 % |
| Vista de video / ventanita del sistema | 72 % | 71 % |
| En pausa | 0 % | 0 % |

**Escritorio (Linux, tema Aurora con luces animadas). Tres corridas: el escritorio tiene más ruido**

| Pantalla | Línea base | 0.4.0 (corridas 1 / 2 / 3) |
|---|---|---|
| Inicio, sonando | 23,7 % | 23,5 / 24,5 / 19,3 % |
| Reproductor, sonando | 18,5 % | 15,6 / 19,8 / 16,3 % |
| Pausa | 0,1 % | 0,1 / 30,9 (*) / 0,1 % |

(*) Una sola vez, sin repetirse: probablemente una tarea de fondo (letras o miniaturas) durante esos 15 s.

En escritorio la mejora es pequeña: ya iba a 30 fps desde la 0.3.1, y con el tema Aurora las luces del fondo se mueven de verdad.
