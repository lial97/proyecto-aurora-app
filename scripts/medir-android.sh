#!/usr/bin/env bash
# Mide el rendimiento de Aurora en el celular conectado por adb. Solo lee: no cambia ajustes del teléfono.
#
#   bash scripts/medir-android.sh arranque        # arranque en frío (5 veces, cierra la app entre cada una)
#   bash scripts/medir-android.sh memoria [etiqueta]   # PSS total de la app en el estado actual
#   bash scripts/medir-android.sh cuadros [segundos]   # cuadros lentos durante N s (por defecto 15) mientras usas la app
#   bash scripts/medir-android.sh cpu [segundos]       # % de CPU medio del proceso durante N s (por defecto 15)
#
# Los resultados se anotan a mano en docs/RENDIMIENTO.md.
set -euo pipefail
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
PKG="${PKG:-app.aurora.music}"
ACT="$PKG/app.aurora.android.MainActivity"

case "${1:-}" in
  arranque)
    total=0
    for i in 1 2 3 4 5; do
      "$ADB" shell am force-stop "$PKG"
      sleep 2
      ms=$("$ADB" shell am start -W -n "$ACT" | awk -F': ' '/TotalTime/ {print $2}' | tr -d '\r')
      echo "arranque $i: ${ms} ms"
      total=$((total + ms))
      sleep 4
    done
    echo "media: $((total / 5)) ms"
    ;;
  memoria)
    pss=$("$ADB" shell dumpsys meminfo "$PKG" | awk '/TOTAL PSS:/ {print $3; exit} /^ *TOTAL / {print $2; exit}' | tr -d '\r')
    echo "memoria ${2:-}: $((pss / 1024)) MB PSS"
    ;;
  cuadros)
    secs="${2:-15}"
    "$ADB" shell dumpsys gfxinfo "$PKG" reset > /dev/null
    echo "Midiendo cuadros durante ${secs} s: usa la app ahora…"
    sleep "$secs"
    "$ADB" shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile" | head -5
    ;;
  cpu)
    secs="${2:-15}"
    pid=$("$ADB" shell pidof "$PKG" | tr -d '\r')
    [ -n "$pid" ] || { echo "La app no está abierta"; exit 1; }
    t0=$("$ADB" shell cat "/proc/$pid/stat" | awk '{print $14 + $15}')
    sleep "$secs"
    t1=$("$ADB" shell cat "/proc/$pid/stat" | awk '{print $14 + $15}')
    # Ticks de 10 ms (CLK_TCK = 100 en Android): % de un núcleo.
    echo "cpu: $(( (t1 - t0) * 10 * 100 / (secs * 1000) )) % de un núcleo en ${secs} s"
    ;;
  *)
    sed -n '2,9p' "$0"; exit 1 ;;
esac
