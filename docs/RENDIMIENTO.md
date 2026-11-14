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
